![](https://minecraft.guntram.de/buttonmaker/?Fabric+Status:/Works/00ff00/ "This mod works with Fabric right now") ![](https://minecraft.guntram.de/buttonmaker/?Forge+Status:/Works/00ff00/ "This mod works with Forge right now") ![](https://minecraft.guntram.de/buttonmaker/?Client+side+mod/No+server+installation/19bfef/ "No need to install on servers")

**When downloading, please make sure to go to the Files tab, and download the version for your modloader. The files have "fabric" or "forge" in their name. Curseforge doesn't always show the correct versions on the main page.**

Want to help translate this mod to your language?
======================================
You can help me translate this mod on https://crowdin.com/project/grid.

Mod description
===============

[For a quick overview, see the screenshots!](https://www.curseforge.com/minecraft/mc-mods/grid/screenshots "screenshots")

Sometimes you want to place blocks in a regular pattern, so you need to count blocks, and if you mess up once, the error propagates to all subsequent blocks.

This mod displays a grid-like overlay over the world, with configurable spacing, so the blocks you're interested in get highlighted automatically.

For example, you're building a railroad and want to place a powered rail ever 20th block. Type /grid 20, walk to the first powered rail, type /grid here (or press the c button, can be configured), and the mod will highlight every 20th block from where you're standing.

Or, if you want to place torches in an area to prevent spawns, type /grid 5 - this highlights every 5th block; placing torches on each of them will light everything up enough.

You can switch between block mode (to highlight blocks to place stuff on) or line mode (to display borders; for example /grid chunks followed by grid lines will turn Grid into a chunk border highlighter), have the y-coordinate of the display floating with the player or fixed, and choose different X and Z spacings if you want that.

Commands:
---------

 

/grid show and /grid hide: show/hide the grid, bound to the B key.

/grid here: set the origin of the grid to where the player is standing. Bound to C (for center)

/grid blocks: set Block mode: show blue squares above marked blocks

/grid lines: set Line mode: show yellow lines to the N/W of marked blocks

/grid chunks: set X and Y spacing to 16 and the origin to chunk borders. Use together with /grid lines to get a chunk border display that's less intrusive than F3-G

/grid &lt;n&gt; &lt;m&gt;: &lt;n&gt; and &lt;m&gt; are numbers; set the grid spacing to n in X direction and m in Y direction

/grid &lt;n&gt;: set the grid spacing to n in both horizontal directions

/grid fixy: toggle between floating the y coordinate of the grid with the player, or fixing it to where the player stands when you use the command. Bound to the Y key.

/grid spawns: show possible mob spawn locations

/grid circles: shows circle overlays. In line mode, this shows regular circles; in block mode, it tries to find and mark the blocks you should build on to get a circle. Obviously, this can't work well with very small diameters, for example, trying to mark a 3x3 circle will give you a square.

/grid distance &lt;n&gt;: sets the range in blocks, that the grid is rendered around you. If you use very large spacings/circle diameters, you need to adjust this too. Be careful though, high values for distance absolutely kill your framerate when you turn spawn locations on.

/grid biome &lt;name&gt;: draw a marker on all blocks in a certain biome. For example, /grid biome river helps when you want to build a drowned or squid farm. (Only with Grid version 1.5+).

/grid hex: Draw a hex grid. In block mode, you get the corners of each hex; in line mode, you get lines around the hexes. (Only with Grid version 1.6+)

 

All keys can be reconfigured using the standard Controls UI.

Fabulous Graphics Warning
=====================

In the 1.16.x versions, Grid has a [bug](https://github.com/gbl/Grid/issues/15 "bug") where things get drawn, even when they should be invisible behind blocks. This doesn't happen with Fast or Fancy graphics, it's just Fabulous that has this bug.
The bug is fixed in the Fabric version, but the fix makes Grid incompatible
to Canvas. In the Forge version, there seems to be no way to fix this,
currently.
