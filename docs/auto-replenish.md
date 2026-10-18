# Auto Replenish

Enable **Player → Auto Replenish**. Low hotbar stacks are topped up from your
main inventory, with the offhand and selected slot handled first.

- **Refill At:** default `8`, configurable from `1–63`. Full stacks are left
  alone, including items with a sixteen-item limit.
- **Delay:** default `2` ticks between refills. Each transaction also waits for
  a short interval based on server latency before another refill.
- **Hotbar:** All Slots by default; Held Slot limits hotbar refills to your
  selected slot.
- **Offhand:** enabled by default.
- **Remember Empty:** enabled by default. An emptied slot retains its item
  identity while you wait for matching supplies.
- **Unstackables:** enabled by default. Replaces consumed or broken items,
  including totems and tools, when their slot becomes empty.
- **Replace Worn Tools:** optional, off by default. Swaps gear at or below
  **Tool Durability** (default `10%`) for the healthiest matching spare.
  The old gear goes into the spare's storage slot.
- **Excluded Items:** comma-separated item IDs, with optional `minecraft:`
  prefixes.

Names, enchantments, potion contents and other components must match. Tool
replacement ignores damage only; it does not replace your equipment with a
differently named or enchanted item.

Refills keep your selected slot unchanged. Stack merges return overflow to its
original storage slot, even with a full inventory. Only main-inventory slots
are used as sources; other hotbar slots, equipped armour and the offhand are
preserved as sources.

The module pauses for item use, inventory screens, occupied cursors, containers,
active Auto Builder work and Order Dropper. After manual inventory editing, it
learns your current layout, including deliberately cleared slots. Empty-slot
memory also resets on disable, player changes and dimension changes.

Only one transaction runs at a time. Three consecutive identical server
rejections trigger a five-second backoff, followed by an automatic retry.
Changed supplies can be considered immediately. It sends native slot clicks
and never uses drop actions.

## Verification

```powershell
./gradlew.bat build runProductionClientGameTest -PautoReplenishTestOnly=true -PmeteorTestJar=C:/path/to/meteor-client-1.21.11-85.jar
```

Production client checks inspect the integrated server's actual inventory:
full-inventory overflow, thresholds, late supplies for remembered empty slots,
offhand and unstackable toggles, names, potion contents, enchantments, healthiest
tool selection, scope/exclusions, inventory/container/cursor/item-use pauses,
rejected click recovery and dimension resets. They also check cursor cleanup,
selected-slot stability and the absence of drop actions.
