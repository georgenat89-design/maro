# Click GUI

Panel shows a flat alphabetical grid with module icons, favorites, search, and filters. Dropdown shows a flat alphabetical list in each category panel. Subsection headings, subsection counts, and Expand/Collapse all controls have been removed. Existing saved section folds cannot hide module rows.

Left-click a module row to open settings. Left-click its switch to toggle it; the star saves a favorite. Right-click anywhere on the row, including its star, switch, or settings dots, toggles the module. Middle-click binds a key.

Settings open in a focused editor. Use the setting tabs to choose a group and the previous/next buttons to change pages. Controls keep their readable size and fit entirely on each page, without nested wheel scrolling. Wider editors use two columns. Visible conditional settings are repaged as their controlling values change. Color controls retain an expandable picker. Back to modules returns to the grid.

Supported modules open their preview in a separate box at the upper right beside the menu. The settings remain visible. Closing the preview keeps the editor open; Show preview reopens it. Escape first closes the preview, then returns to modules. Play/Stop affect the local preview only.

The grid footer has previous/next module pages with a short slide/fade transition. Page Up and Page Down change module pages while the grid is active; typing into a field keeps those keys in the field. Mouse-wheel module browsing retains its smooth glide and immediate reversal.

Appearance offers Size, Colors, and Interface tabs without scrolling. Size includes UI Scale, Fullscreen, and Text Size (75–110%). Text and category navigation resize together; category tabs wrap when needed. The saved custom scale is retained when Fullscreen fills 97% of the viewport. UI scale is applied on release to keep the slider under the pointer. Colors changes the shared live accent, including category icons and Staff Watch. Interface contains Frosted Glass, UI Sounds, and the full client settings page. Client and theme controls also use pages.

Presets, Accounts, Style, and Setup remain in the toolbar. Layouts share module settings, binds, configurations, and theme preferences. Dedicated module tools retain their own screens.

## Build and verify

Use Java 21. Run `gradlew.bat remapJar remapGametestJar`, then `gradlew.bat runProductionClientGameTest -PcleanDesignOnly=true` for the settings, paging, local jump playback and resize checks. The native test uses the project’s separate `run` profile and creates a test world. Release JARs are in `build/libs`.
