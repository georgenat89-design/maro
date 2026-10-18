# Module-list scrolling

The shared scrollbar now accepts track clicks and thumb dragging in module grids,
category panels and module settings. Its wider hit region is clipped to the containing
GUI. Track clicks center the thumb; dragging captures movement until release, even
outside the list. Drag updates are immediate while wheel input retains frame-independent
easing. The thumb is centered inside its track with an inset at both ends. Client
appearance cards reserve a separate scrollbar gutter. Bounds and thumb size are clamped when the list or viewport changes, and
nonfinite wheel values are ignored. Drawing and input use the same GUI transform.

Module lists now measure section headers and expanded module rows together before
setting scroll bounds. Folding clamps the current offset to the new content height;
global expand/fold controls return the lists to the top. A search ignores saved folds
so matches are always reachable, then restores the normal folded view when cleared.
Both layouts use the same section metadata and persistent fold IDs. Keep new section
IDs stable in module/ModuleSections.java; assign new module names there. Unassigned
modules fall back to General in their existing category and are never omitted.

Native checks exercise ten wheel events, track click, drag past both ends, release outside,
reverse wheel after dragging and content shrink in Panel and Dropdown at GUI scales 2/4.

Wheel and touchpad scrolling now use a critically damped spring with fractional input,
immediate direction changes and no overshoot. Content becomes visible immediately while
scrolling, including cards entering from outside the viewport. Settings measure their
current expanded rows before applying scroll bounds, so resizing and changing settings
visibility cannot leave a frame of stale clipping or scroll position.

Dragging a control freezes pending wheel movement. Scale drags also hold the currently
drawn window transform until release, including when a previous resize is still settling.
Additional wheel input is ignored during a drag. Resolution changes clear stale pointer
capture. Deterministic checks cover 30/60/144 Hz, fractional input, reversal, viewport
shrink and invalid input; native checks cover swiping then grabbing the scale slider
mid-animation, frozen offsets, fullscreen and both layouts at GUI scales 2/4.

A bottom-right Dropdown / Panel selector is available directly in both layouts.
It switches between the category panels and card grid, saves the preference,
and preserves the selected category and search. Each layout reserves space for the control.
The switch closes open dropdowns, text editing, keybind capture and drag state before changing
layouts, without toggling modules.

Each expanded category has a bottom grip. Left-drag it up or down to change how many rows
are visible, with capture until mouse release outside the panel. Height is clamped to the
available viewport, stored per category in client settings and retained across filtering.
Right-click the grip to restore automatic height. Reset Layout also resets these heights.
The hints sit in the reserved footer rather than covering the newly exposed module rows.

Saved Window, Laptop and iPad Click GUI choices migrate to Panel. The phone's iPad
device is separate and remains available. Both Click GUI styles share eased scale,
position and height transitions with a short fade when switching. Hit testing uses
the currently drawn transform throughout the transition. Close uses a smooth 260ms curve.
Fullscreen View expands the available GUI canvas to 97% of the screen in either layout.
It preserves the saved custom scale and returns to that size when switched off.
