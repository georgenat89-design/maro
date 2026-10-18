package dev.maro.render.pickaxe;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.*;

/** Immutable, beveled 3D geometry. Built once; rendering does not rebuild the mesh. */
public final class PickaxeMesh {
    private record Face(float[] p,float nx,float ny,float nz,int color,boolean metal,float[] smooth,boolean emissive){}
    private final Face[] faces;
    public final Vector3fc[] bounds;
    private PickaxeMesh(List<Face> faces){
        this.faces=faces.toArray(Face[]::new);float lx=Float.POSITIVE_INFINITY,ly=lx,lz=lx,hx=-lx,hy=-lx,hz=-lx;
        for(var face:this.faces)for(int i=0;i<12;i+=3){lx=Math.min(lx,face.p[i]);ly=Math.min(ly,face.p[i+1]);lz=Math.min(lz,face.p[i+2]);hx=Math.max(hx,face.p[i]);hy=Math.max(hy,face.p[i+1]);hz=Math.max(hz,face.p[i+2]);}
        bounds=new Vector3fc[8];for(int i=0;i<8;i++)bounds[i]=new Vector3f((i&1)==0?lx:hx,(i&2)==0?ly:hy,(i&4)==0?lz:hz);
    }
    public int faces(){return faces.length;}
    public boolean finite(){for(var f:faces){for(float v:f.p)if(!Float.isFinite(v))return false;if(f.smooth!=null)for(float v:f.smooth)if(!Float.isFinite(v))return false;if(!Float.isFinite(f.nx+f.ny+f.nz))return false;}return true;}
    public void emit(MatrixStack.Entry pose,VertexConsumer out,int light,int overlay,float polish,boolean outline,int outlineColor,float width){
        var n=new Vector3f();var view=new Vector3f();var half=new Vector3f();float time=(System.nanoTime()%60_000_000_000L)/1e9f;
        for(var f:faces){
            pose.getPositionMatrix().transformPosition((f.p[0]+f.p[3]+f.p[6]+f.p[9])*.25f,(f.p[1]+f.p[4]+f.p[7]+f.p[10])*.25f,(f.p[2]+f.p[5]+f.p[8]+f.p[11])*.25f,view);view.negate().normalize();
            half.set(view).add(.3f,.65f,.85f).normalize();
            for(int v=0;v<4;v++){
                int p=(outline?3-v:v)*3;float offset=outline?width:0;
                float nx=f.smooth==null?f.nx:f.smooth[p],ny=f.smooth==null?f.ny:f.smooth[p+1],nz=f.smooth==null?f.nz:f.smooth[p+2];
                int color=outline?outlineColor:f.color;
                n.set(nx,ny,nz);pose.getNormalMatrix().transform(n);n.normalize();
                if(!outline&&!f.emissive)color=shade(color,.74f+.26f*Math.max(0,-n.x*.25f+n.y*.65f+n.z*.72f));
                if(!outline&&f.metal){
                    float spec=Math.max(0,n.dot(half));spec*=spec;spec*=spec;spec*=spec;spec*=spec;spec*=spec;
                    float reflected=2*n.y*n.dot(view)-view.y,band=(reflected-.1f)*5;
                    color=shade(color,1+polish*(.30f*Math.clamp((reflected+.5f)/1.2f,0,1)+.70f/(1+band*band)+spec*.85f));
                }
                float flame=f.emissive&&(f.color>>8&255)>(f.color>>16&255)+45?Math.max(0,f.p[p+1]-.55f):0;
                float drift=(float)Math.sin(time*3.2f+f.p[p+1]*11+f.p[p+2]*6)*flame*.055f;
                out.vertex(pose,f.p[p]+nx*offset+drift,f.p[p+1]+ny*offset,f.p[p+2]+nz*offset+drift*.3f).color(color)
                    .texture(.45f,.04f).overlay(overlay).light(outline||f.emissive?0xF000F0:light).normal(pose,nx,ny,nz);
            }
        }
    }
    private static int shade(int c,float v){return (c&0xFF000000)|Math.clamp(Math.round((c>>16&255)*v),0,255)<<16|Math.clamp(Math.round((c>>8&255)*v),0,255)<<8|Math.clamp(Math.round((c&255)*v),0,255);}
    public static final class Builder {
        private final List<Face> faces=new ArrayList<>();
        public PickaxeMesh build(){return new PickaxeMesh(faces);}
        public void glow(Runnable action){int start=faces.size();action.run();for(int i=start;i<faces.size();i++){var f=faces.get(i);faces.set(i,new Face(f.p,f.nx,f.ny,f.nz,f.color,f.metal,f.smooth,true));}}
        private static Vector3f at(double x,double y,double z){return new Vector3f((float)x,(float)y,(float)z);}
        /** Elliptical loft rows {centerX,y,centerZ,radiusX,radiusZ}; optional radial surface relief. */
        public void loft(double[][] rows,int sides,int color,boolean metal,boolean smooth,java.util.function.DoubleBinaryOperator relief){
            Vector3f[][] rings=new Vector3f[rows.length][sides];
            for(int r=0;r<rows.length;r++)for(int c=0;c<sides;c++){
                double a=c*Math.PI*2/sides,k=relief==null?1:relief.applyAsDouble((double)r/(rows.length-1),a);
                rings[r][c]=at(rows[r][0]+Math.cos(a)*rows[r][3]*k,rows[r][1],rows[r][2]+Math.sin(a)*rows[r][4]*k);
            }
            for(int r=0;r<rows.length-1;r++)for(int c=0;c<sides;c++){
                int n=(c+1)%sides;var row=rows[r];var row2=rows[r+1];
                facing(rings[r][c],rings[r+1][c],rings[r+1][n],rings[r][n],color,metal,at(Math.cos((c+.5)*Math.PI*2/sides),0,Math.sin((c+.5)*Math.PI*2/sides)));
                if(smooth)smoothLast(pt->{double t=Math.clamp((pt.y-row[1])/(row2[1]-row[1]),0,1),cx=row[0]+(row2[0]-row[0])*t,cz=row[2]+(row2[2]-row[2])*t,rx=row[3]+(row2[3]-row[3])*t,rz=row[4]+(row2[4]-row[4])*t;return at((pt.x-cx)/(rx*rx),0,(pt.z-cz)/(rz*rz));});
            }
            for(int r:new int[]{0,rows.length-1})for(int c=0;c<sides;c++){var row=rows[r];var center=at(row[0],row[1],row[2]);facing(center,rings[r][c],rings[r][(c+1)%sides],rings[r][(c+1)%sides],color,metal,at(0,r==0?-1:1,0));}
        }
        /** A shallow two-sided ribbon with explicit 3D edges, useful for cloth and flame tongues. */
        public void ribbon(double[][] left,double[][] right,int color,boolean metal){
            for(int i=0;i<left.length-1;i++){var a=at(left[i][0],left[i][1],left[i][2]);var b=at(right[i][0],right[i][1],right[i][2]);var c=at(right[i+1][0],right[i+1][1],right[i+1][2]);var d=at(left[i+1][0],left[i+1][1],left[i+1][2]);quad(a,b,c,d,color,metal);quad(d,c,b,a,color,metal);}
        }
        private void quad(Vector3fc a,Vector3fc b,Vector3fc c,Vector3fc d,int color,boolean metal){
            var n=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));if(n.lengthSquared()<1e-14f)n.set(c).sub(a).cross(new Vector3f(d).sub(a));if(n.lengthSquared()<1e-14f)return;n.normalize();
            faces.add(new Face(new float[]{a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),c.x(),c.y(),c.z(),d.x(),d.y(),d.z()},n.x,n.y,n.z,color,metal,null,false));
        }
        private void smoothLast(java.util.function.Function<Vector3f,Vector3f> normal){var f=faces.removeLast();float[] ns=new float[12];for(int i=0;i<12;i+=3){var n=normal.apply(new Vector3f(f.p[i],f.p[i+1],f.p[i+2])).normalize();ns[i]=n.x;ns[i+1]=n.y;ns[i+2]=n.z;}faces.add(new Face(f.p,f.nx,f.ny,f.nz,f.color,f.metal,ns,f.emissive));}
        private void facing(Vector3fc a,Vector3fc b,Vector3fc c,Vector3fc d,int color,boolean metal,Vector3fc outward){
            var n=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));if(n.lengthSquared()<1e-14f)n.set(c).sub(a).cross(new Vector3f(d).sub(a));if(n.dot(outward)<0)quad(a,d,c,b,color,metal);else quad(a,b,c,d,color,metal);
        }
        public void box(double x,double y,double z,double w,double h,double depth,int color,boolean metal){
            Vector3f[] p=new Vector3f[8];for(int i=0;i<8;i++)p[i]=at(x+((i&1)==0?-w:w)/2,y+((i&2)==0?-h:h)/2,z+((i&4)==0?-depth:depth)/2);
            int[][] q={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
            for(var f:q){var center=new Vector3f(p[f[0]]).add(p[f[1]]).add(p[f[2]]).add(p[f[3]]).mul(.25f).sub((float)x,(float)y,(float)z);facing(p[f[0]],p[f[1]],p[f[2]],p[f[3]],color,metal,center);}
        }
        public void cylinder(double ax,double ay,double az,double bx,double by,double bz,double radius,int color,boolean metal){
            var a=at(ax,ay,az);var b=at(bx,by,bz);var axis=new Vector3f(b).sub(a).normalize();
            var u=new Vector3f(axis).cross(Math.abs(axis.y)<.9?new Vector3f(0,1,0):new Vector3f(0,0,1)).normalize();var v=new Vector3f(axis).cross(u);
            int sides=radius<.008?6:radius<.015?12:24;
            for(int i=0;i<sides;i++){
                double p=i*Math.PI*2/sides,q=(i+1)*Math.PI*2/sides;
                var d1=new Vector3f(u).mul((float)(Math.cos(p)*radius)).fma((float)(Math.sin(p)*radius),v);
                var d2=new Vector3f(u).mul((float)(Math.cos(q)*radius)).fma((float)(Math.sin(q)*radius),v);
                var a1=new Vector3f(a).add(d1);var a2=new Vector3f(a).add(d2);var b1=new Vector3f(b).add(d1);var b2=new Vector3f(b).add(d2);
                facing(a1,a2,b2,b1,color,metal,new Vector3f(d1).add(d2));smoothLast(pt->{var d=pt.sub(a);return d.fma(-d.dot(axis),axis);});facing(a,a1,a2,a2,color,metal,new Vector3f(axis).negate());facing(b,b2,b1,b1,color,metal,axis);
            }
        }
        /** Continuous tapered tube; optional diagonal stripes follow the bend rather than becoming rings. */
        public void tube(double[][] path,double[] radii,int color,int stripe,double turns,boolean metal){
            Vector3f[][] rings=new Vector3f[path.length][24];Vector3f[][] normals=new Vector3f[path.length][24];
            for(int row=0;row<path.length;row++){
                var c=at(path[row][0],path[row][1],path[row][2]);var a=path[Math.max(0,row-1)];var b=path[Math.min(path.length-1,row+1)];
                var axis=at(b[0]-a[0],b[1]-a[1],b[2]-a[2]).normalize();var u=new Vector3f(0,0,1);var v=new Vector3f(axis).cross(u).normalize();
                for(int col=0;col<24;col++){double angle=col*Math.PI/12+(stripe==0?0:turns*Math.PI*2*row/(path.length-1));var normal=new Vector3f(u).mul((float)Math.cos(angle)).fma((float)Math.sin(angle),v);normals[row][col]=normal;rings[row][col]=new Vector3f(c).fma((float)radii[row],normal);}
            }
            for(int row=0;row<path.length-1;row++)for(int col=0;col<24;col++){
                int next=(col+1)%24;int tint=stripe!=0&&col<11?stripe:color;
                var outward=new Vector3f(normals[row][col]).add(normals[row][next]);
                facing(rings[row][col],rings[row][next],rings[row+1][next],rings[row+1][col],tint,metal,outward);
                var f=faces.removeLast();float[] ns=new float[12];for(int i=0;i<12;i+=3){var pt=at(f.p[i],f.p[i+1],f.p[i+2]);int best=0;float distance=Float.MAX_VALUE;Vector3f[] candidates={rings[row][col],rings[row][next],rings[row+1][next],rings[row+1][col]};for(int k=0;k<4;k++){float d=candidates[k].distanceSquared(pt);if(d<distance){distance=d;best=k;}}var n=normals[best<2?row:row+1][best==0||best==3?col:next];ns[i]=n.x;ns[i+1]=n.y;ns[i+2]=n.z;}faces.add(new Face(f.p,f.nx,f.ny,f.nz,tint,metal,ns,f.emissive));
            }
            for(int end:new int[]{0,path.length-1}){var center=at(path[end][0],path[end][1],path[end][2]);var d=at(path[Math.min(path.length-1,end+1)][0]-path[Math.max(0,end-1)][0],path[Math.min(path.length-1,end+1)][1]-path[Math.max(0,end-1)][1],path[Math.min(path.length-1,end+1)][2]-path[Math.max(0,end-1)][2]);if(end==0)d.negate();for(int col=0;col<24;col++)facing(center,rings[end][col],rings[end][(col+1)%24],rings[end][(col+1)%24],color,metal,d);}
        }
        public void sphere(double x,double y,double z,double rx,double ry,double rz,int color,boolean metal){
            for(int row=0;row<8;row++)for(int col=0;col<16;col++){
                Vector3f[] p=new Vector3f[4];int[] rr={row,row,row+1,row+1},cc={col,col+1,col+1,col};
                for(int i=0;i<4;i++){double a=rr[i]*Math.PI/8-Math.PI/2,t=cc[i]*Math.PI/8;p[i]=at(x+Math.cos(a)*Math.cos(t)*rx,y+Math.sin(a)*ry,z+Math.cos(a)*Math.sin(t)*rz);}
                var center=new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]).mul(.25f).sub((float)x,(float)y,(float)z);facing(p[0],p[1],p[2],p[3],color,metal,center);
                smoothLast(pt->pt.sub((float)x,(float)y,(float)z).div((float)(rx*rx),(float)(ry*ry),(float)(rz*rz)));
            }
        }
        /** Ear-clipped concave silhouette with a bevel and a cupped blade face. */
        public void blade(double[][] points,double depth,double bevel,double curve,int color,int rim){
            double cx=0,cy=0,area=0;for(int i=0;i<points.length;i++){cx+=points[i][0];cy+=points[i][1];var a=points[i];var b=points[(i+1)%points.length];area+=a[0]*b[1]-b[0]*a[1];}cx/=points.length;cy/=points.length;
            double[][] p=new double[points.length][2];for(int i=0;i<p.length;i++)p[i]=points[area>=0?i:p.length-1-i].clone();
            double[][] inside=new double[p.length][2];for(int i=0;i<p.length;i++){inside[i][0]=cx+(p[i][0]-cx)*(1-bevel);inside[i][1]=cy+(p[i][1]-cy)*(1-bevel);}
            for(int i=0;i<p.length;i++){
                int j=(i+1)%p.length;var a=at(p[i][0],p[i][1],depth*.25);var b=at(p[j][0],p[j][1],depth*.25);
                var c=at(inside[j][0],inside[j][1],surface(inside[j],cy,depth,curve));var d=at(inside[i][0],inside[i][1],surface(inside[i],cy,depth,curve));quad(a,b,c,d,rim,true);
                var loA=at(p[i][0],p[i][1],-depth*.5);var loB=at(p[j][0],p[j][1],-depth*.5);
                facing(a,loA,loB,b,rim,true,at((p[i][0]+p[j][0])*.5-cx,(p[i][1]+p[j][1])*.5-cy,0));
            }
            var ids=new ArrayList<Integer>();for(int i=0;i<p.length;i++)ids.add(i);int guard=0;
            while(ids.size()>2&&guard++<p.length*p.length){
                boolean cut=false;for(int i=0;i<ids.size();i++){
                    int ia=ids.get(Math.floorMod(i-1,ids.size())),ib=ids.get(i),ic=ids.get((i+1)%ids.size());
                    if(cross(inside[ia],inside[ib],inside[ic])<=1e-10)continue;
                    boolean occupied=false;for(int id:ids)if(id!=ia&&id!=ib&&id!=ic&&contains(inside[id],inside[ia],inside[ib],inside[ic])){occupied=true;break;}
                    if(occupied)continue;
                    triangle(inside[ia],inside[ib],inside[ic],cy,depth,curve,color,2);
                    var a=at(p[ia][0],p[ia][1],-depth*.5);var b=at(p[ic][0],p[ic][1],-depth*.5);var c=at(p[ib][0],p[ib][1],-depth*.5);quad(a,b,c,c,color,true);
                    ids.remove(i);cut=true;break;
                }if(!cut)throw new IllegalArgumentException("Invalid pickaxe blade silhouette");
            }
        }
        private static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
        private static boolean contains(double[] p,double[] a,double[] b,double[] c){return cross(a,b,p)>=-1e-10&&cross(b,c,p)>=-1e-10&&cross(c,a,p)>=-1e-10;}
        private static double surface(double[] p,double cy,double depth,double curve){return depth*.5+curve*(p[0]*p[0]+(p[1]-cy)*(p[1]-cy));}
        private void triangle(double[] a,double[] b,double[] c,double cy,double depth,double curve,int color,int subdivisions){
            if(subdivisions>0){var ab=new double[]{(a[0]+b[0])/2,(a[1]+b[1])/2};var bc=new double[]{(b[0]+c[0])/2,(b[1]+c[1])/2};var ca=new double[]{(c[0]+a[0])/2,(c[1]+a[1])/2};triangle(a,ab,ca,cy,depth,curve,color,subdivisions-1);triangle(ab,b,bc,cy,depth,curve,color,subdivisions-1);triangle(ca,bc,c,cy,depth,curve,color,subdivisions-1);triangle(ab,bc,ca,cy,depth,curve,color,subdivisions-1);return;}
            var x=at(a[0],a[1],surface(a,cy,depth,curve));var y=at(b[0],b[1],surface(b,cy,depth,curve));var z=at(c[0],c[1],surface(c,cy,depth,curve));quad(x,y,z,z,color,true);smoothLast(pt->at(-2*curve*pt.x,-2*curve*(pt.y-cy),1));
        }
    }
}
