package dev.maro.chunkpulse;

import java.util.*;

/** Persistent discovery identities are owned by chunk footprints, never by a changing flood-fill seed. */
public final class ClusterDiscovery {
    public enum State { HIDDEN, REVEALED }
    public enum Mode { DIG_DOWN, APPROACH, ALIGN }
    public record Snapshot(String id,State state,List<Long> footprint,long created,long changed,long seen,String reason) { }
    public record Transition(long time,String id,State from,State to,String reason) { }
    public record Anchor(double x,double y,double z) { }
    public record View(String id,State state,ClusterEngine.Cluster cluster,Set<Long> footprint,int topY,List<Anchor> machines) {
        public boolean inside(SelfActivityFilter.Position p){return footprint.contains(SignalEvent.key((int)Math.floor(p.x())>>4,(int)Math.floor(p.z())>>4));}
        public double depth(SelfActivityFilter.Position p){return Math.max(0,p.y()-topY);}
        public double distance(SelfActivityFilter.Position p){var c=cluster;return Math.sqrt(sq(c.x()-p.x())+sq(c.y()-p.y())+sq(c.z()-p.z()));}
        public double bandDistance(SelfActivityFilter.Position p){return Math.max(0,Math.max(cluster.minY()-p.y(),p.y()-cluster.maxY()));}
    }
    public record Guidance(View target,Mode mode,double depth,double horizontal,double dx,double dz) {
        public String offset(){return direction(dx,dz);}
    }
    private static final int MAX_ENTRIES=4096,MAX_CHUNKS=32768;
    private static final class Entry {
        final String id;final Set<Long> footprint=new LinkedHashSet<>();final long created;
        State state=State.HIDDEN;long changed,seen,dwell=-1;String reason="NEW";
        Entry(String id,long now){this.id=id;created=changed=seen=now;}
        Snapshot snapshot(){return new Snapshot(id,state,footprint.stream().sorted().toList(),created,changed,seen,reason);}
    }
    private final PulseConfig config;
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private final Map<Long,Entry> owners=new HashMap<>(),pendingFound=new HashMap<>();
    private final LinkedHashMap<Long,String> exposed=new LinkedHashMap<>();
    private final ArrayDeque<Transition> transitions=new ArrayDeque<>();
    private final ArrayDeque<String> cues=new ArrayDeque<>();
    private final Set<String> inside=new HashSet<>(),cued;
    private final String cuePrefix;
    private List<View> views=List.of();
    private long serial,revision;
    public ClusterDiscovery(PulseConfig c){this(c,new HashSet<>(),"");}
    public ClusterDiscovery(PulseConfig c,Set<String> sessionCues,String cuePrefix){config=c;cued=sessionCues;this.cuePrefix=cuePrefix;}
    private static double sq(double n){return n*n;}
    public long revision(){return revision;}
    public List<View> views(){return views;}
    public List<Transition> transitions(){return List.copyOf(transitions);}
    public List<String> drainCues(){var out=List.copyOf(cues);cues.clear();return out;}
    public boolean revealed(long key){var e=owners.get(key);if(e==null)e=pendingFound.get(key);return e!=null&&e.state==State.REVEALED;}
    public String id(long key){var e=owners.get(key);if(e==null)e=pendingFound.get(key);return e==null?"":e.id;}
    public State state(String id){var e=entries.get(id);return e==null?null:e.state;}
    private void transition(Entry e,State state,long now,String reason){
        State from=e.state;e.state=state;e.changed=now;e.reason=reason;e.dwell=-1;revision++;
        transitions.addLast(new Transition(now,e.id,from,state,reason));while(transitions.size()>256)transitions.removeFirst();
    }
    private Entry create(List<ChunkStats> chunks,long now){
        if(entries.size()>=MAX_ENTRIES||owners.size()+chunks.size()>MAX_CHUNKS)return null;
        String id;do{id="cp-"+Long.toUnsignedString(now,36)+"-"+Long.toUnsignedString(++serial,36);}while(entries.containsKey(id));
        var e=new Entry(id,now);entries.put(id,e);attach(e,chunks,now);return e;
    }
    private void attach(Entry e,List<ChunkStats> chunks,long now){for(var s:chunks){long k=SignalEvent.key(s.x,s.z);if(!owners.containsKey(k)&&owners.size()<MAX_CHUNKS){e.footprint.add(k);owners.put(k,e);e.changed=now;}}}
    private static boolean adjacent(Set<Long> keys,Entry e){for(long key:keys){int x=SignalEvent.chunkX(key),z=SignalEvent.chunkZ(key);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(e.footprint.contains(SignalEvent.key(x+dx,z+dz)))return true;}return false;}
    public void update(List<ClusterEngine.Cluster> clusters,SelfActivityFilter.Position player,long now){
        pendingFound.clear();
        for(var c:clusters){
            var hidden=new LinkedHashSet<Entry>();var found=new LinkedHashSet<Entry>();var fresh=new ArrayList<ChunkStats>();
            for(var s:c.chunks()){var e=owners.get(SignalEvent.key(s.x,s.z));if(e==null)fresh.add(s);else if(e.state==State.HIDDEN)hidden.add(e);else found.add(e);}
            for(var s:fresh)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var e=owners.get(SignalEvent.key(s.x+dx,s.z+dz));if(e!=null&&e.state==State.REVEALED)found.add(e);}
            if(!fresh.isEmpty()){
                if(found.isEmpty()){if(hidden.isEmpty())create(fresh,now);else attach(hidden.iterator().next(),fresh,now);}
                else {
                    // Found footprints stay frozen. Analyse only the new part; old clocks/score cannot
                    // promote a few newly observed chunks into a fresh hidden discovery.
                    for(var component:ClusterEngine.clusters(fresh,now,config)){
                        Set<Long> keys=new HashSet<>();for(var s:component.chunks())keys.add(SignalEvent.key(s.x,s.z));
                        Entry neighbour=hidden.stream().filter(e->adjacent(keys,e)).findFirst().orElse(null);
                        if(neighbour!=null)attach(neighbour,component.chunks(),now);
                        else if(component.tier().ordinal()>=2&&component.chunks().size()>=config.splitChunks&&component.chunks().stream().allMatch(s->now-s.last()<=10000&&s.score(now,config)>=config.high))create(component.chunks(),now);
                        else for(long k:keys)pendingFound.put(k,found.iterator().next());
                    }
                }
            }
        }
        var grouped=new LinkedHashMap<Entry,List<ChunkStats>>();
        for(var c:clusters)for(var s:c.chunks()){var e=owners.get(SignalEvent.key(s.x,s.z));if(e!=null)grouped.computeIfAbsent(e,k->new ArrayList<>()).add(s);}
        var next=new ArrayList<View>();
        for(var group:grouped.entrySet()){
            var e=group.getKey();var v=view(e,group.getValue(),now);e.seen=Math.max(e.seen,v.cluster().last());
            if(e.state==State.HIDDEN){
                String exposedReason=null;for(long k:v.footprint())if(exposed.containsKey(k)){exposedReason=exposed.get(k);break;}
                if(exposedReason!=null)transition(e,State.REVEALED,now,exposedReason);
                else if(player!=null&&nearMachine(v,player))transition(e,State.REVEALED,now,"C1_EXACT_MACHINE / INITIAL_NEAR");
                else if(player!=null&&entirelyNear(v,player))transition(e,State.REVEALED,now,"INITIAL_NEAR_ACTIVITY");
            }
            next.add(withState(v,e.state));
        }
        views=List.copyOf(next);tick(player,now);
    }
    private View view(Entry e,List<ChunkStats> records,long now){
        var groups=ClusterEngine.clusters(records,now,config);var first=groups.getFirst();
        double total=0,x=0,y=0,z=0;int positions=0,minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=minX,maxZ=maxX,minY=minX,maxY=maxX,top=Integer.MIN_VALUE,exactTop=Integer.MIN_VALUE;
        long last=0;var tier=ClusterEngine.Tier.LOW;var anchors=new ArrayList<Anchor>();var footprint=new LinkedHashSet<Long>();
        for(var c:groups){total+=c.score();x+=c.x()*c.score();y+=c.y()*c.score();z+=c.z()*c.score();positions+=c.positions();last=Math.max(last,c.last());if(c.tier().ordinal()>tier.ordinal())tier=c.tier();
            minX=Math.min(minX,c.minX());maxX=Math.max(maxX,c.maxX());minZ=Math.min(minZ,c.minZ());maxZ=Math.max(maxZ,c.maxZ());minY=Math.min(minY,c.minY());maxY=Math.max(maxY,c.maxY());}
        for(var s:records){footprint.add(SignalEvent.key(s.x,s.z));for(var p:s.points()){exactTop=Math.max(exactTop,p.y());if(p.machine()){top=Math.max(top,p.y());anchors.add(new Anchor(p.x()+.5,p.y()+.5,p.z()+.5));}}}
        var aggregate=new ClusterEngine.Cluster(first.id(),List.copyOf(records),total,tier,x/total,y/total,z/total,minX,maxX,minZ,maxZ,minY,maxY,positions,last,first.explanation());
        return new View(e.id,e.state,aggregate,Set.copyOf(footprint),top!=Integer.MIN_VALUE?top:exactTop!=Integer.MIN_VALUE?exactTop:maxY,List.copyOf(anchors));
    }
    private static View withState(View v,State state){return new View(v.id(),state,v.cluster(),v.footprint(),v.topY(),v.machines());}
    private boolean nearMachine(View v,SelfActivityFilter.Position p){double radius=sq(config.revealRadius);for(var a:v.machines())if(p.distance2(a.x(),a.y(),a.z())<=radius)return true;return false;}
    private boolean entirelyNear(View v,SelfActivityFilter.Position p){var c=v.cluster();for(double x:new double[]{c.minX(),c.maxX()})for(double y:new double[]{c.minY(),c.maxY()})for(double z:new double[]{c.minZ(),c.maxZ()})if(p.distance2(x,y,z)>sq(config.revealRadius))return false;return true;}
    public void tick(SelfActivityFilter.Position player,long now){
        if(player==null)return;var next=new ArrayList<View>();var currentInside=new HashSet<String>();
        for(var v:views){var e=entries.get(v.id());boolean in=v.inside(player);
            if(e.state==State.HIDDEN){
                if(nearMachine(v,player))transition(e,State.REVEALED,now,"C1_EXACT_MACHINE");
                else if(in&&player.y()>=v.cluster().minY()-8&&player.y()<=v.cluster().maxY()+8){if(e.dwell<0)e.dwell=now;else if(now-e.dwell>=5000)transition(e,State.REVEALED,now,"C2_BAND_DWELL");}else e.dwell=-1;
            }
            if(e.state==State.HIDDEN&&in){currentInside.add(e.id);if(!inside.contains(e.id)&&v.cluster().tier().ordinal()>=2&&eligible(v,player)&&config.signalEnabled&&config.digCue&&cued.size()<32768&&cued.add(cuePrefix+e.id))cues.addLast(e.id);}
            next.add(withState(v,e.state));
        }
        inside.clear();inside.addAll(currentInside);views=List.copyOf(next);
    }
    /** Exact incoming machine events can reveal an existing cluster even when self filters discard their score. */
    public void observeMachine(int x,int y,int z,SelfActivityFilter.Position player,long now,boolean blockUpdate){
        if(player==null||y<config.minY||y>=config.maxY||player.distance2(x+.5,y+.5,z+.5)>sq(config.revealRadius))return;
        long key=SignalEvent.key(x>>4,z>>4);String reason=blockUpdate?"C3_BLOCK_UPDATE":"C1_EXACT_MACHINE";
        exposed.remove(key);exposed.put(key,reason);while(exposed.size()>config.maxChunks)exposed.remove(exposed.keySet().iterator().next());
        var e=owners.get(key);if(e!=null&&e.state==State.HIDDEN){transition(e,State.REVEALED,now,reason);views=views.stream().map(v->v.id().equals(e.id)?withState(v,e.state):v).toList();}
    }
    private boolean eligible(View v,SelfActivityFilter.Position p){return v.state()==State.HIDDEN&&v.cluster().tier().ordinal()>=config.signalMinTier.ordinal()&&(p.y()>v.topY()||v.bandDistance(p)<=config.alignRadius);}
    private Comparator<View> nearest(SelfActivityFilter.Position p){return Comparator.comparingLong((View v)->Math.round(v.distance(p)*1000)).thenComparing(Comparator.comparingInt((View v)->v.cluster().tier().ordinal()).reversed()).thenComparing(View::id);}
    public Guidance guidance(SelfActivityFilter.Position player){
        if(player==null||!config.signalEnabled)return null;
        var eligible=views.stream().filter(v->eligible(v,player)).toList();var target=eligible.stream().filter(v->v.inside(player)).min(nearest(player)).orElseGet(()->eligible.stream().min(nearest(player)).orElse(null));if(target==null)return null;
        double dx=target.cluster().x()-player.x(),dz=target.cluster().z()-player.z();
        Mode mode=target.inside(player)?target.bandDistance(player)<=config.alignRadius?Mode.ALIGN:Mode.DIG_DOWN:Mode.APPROACH;
        return new Guidance(target,mode,target.depth(player),Math.hypot(dx,dz),dx,dz);
    }
    public String mark(String id,boolean found,SelfActivityFilter.Position player,long now){
        if(id==null||id.isBlank())id=player==null?null:views.stream().min(nearest(player)).map(View::id).orElse(null);
        var direct=entries.get(id);String requested=id;var e=direct!=null?direct:entries.values().stream().filter(entry->entry.id.equalsIgnoreCase(requested)).findFirst().orElse(null);if(e==null)return null;transition(e,found?State.REVEALED:State.HIDDEN,now,found?"C4_MANUAL_FOUND":"MANUAL_UNFOUND");
        if(!found){for(long k:e.footprint)exposed.remove(k);inside.remove(e.id);}
        views=views.stream().map(v->v.id().equals(e.id)?withState(v,e.state):v).toList();return e.id;
    }
    public List<Snapshot> snapshots(){return entries.values().stream().map(Entry::snapshot).toList();}
    public void restore(List<Snapshot> saved){
        if(saved==null)return;
        for(var s:saved.stream().filter(Objects::nonNull).sorted(Comparator.comparing((Snapshot s)->s.state()==State.REVEALED).reversed()).toList()){
            if(entries.size()>=MAX_ENTRIES)break;
            if(s.id()==null||!s.id().matches("[A-Za-z0-9_.-]{1,80}")||s.state()==null||s.footprint()==null||s.created()<0||s.changed()<s.created()||entries.containsKey(s.id()))continue;
            var e=new Entry(s.id(),s.created());e.state=s.state();e.changed=s.changed();e.seen=s.seen();e.reason=s.reason()==null?"RESTORED":s.reason().substring(0,Math.min(160,s.reason().length()));
            for(var k:s.footprint()){if(owners.size()>=MAX_CHUNKS)break;if(k!=null&&!owners.containsKey(k)&&Math.abs((long)SignalEvent.chunkX(k))<=1875000&&Math.abs((long)SignalEvent.chunkZ(k))<=1875000){e.footprint.add(k);owners.put(k,e);}}
            if(!e.footprint.isEmpty())entries.put(e.id,e);
        }
    }
    public static String direction(double dx,double dz){String x=Math.abs(dx)<.75?"":Math.round(Math.abs(dx))+(dx>0?" E":" W"),z=Math.abs(dz)<.75?"":Math.round(Math.abs(dz))+(dz>0?" S":" N");return x.isEmpty()?z.isEmpty()?"AT CENTRE":z:z.isEmpty()?x:x+" / "+z;}
}
