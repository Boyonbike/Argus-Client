package dev.randomstuff.client.gui;

/** Independent pixel density keeps the menu compact even when Minecraft's GUI is enlarged. */
record GuiViewport(int width,int height,float renderScale) {
    static GuiViewport create(int framebufferWidth,int framebufferHeight,double minecraftScale,double userScale){
        double preference=Double.isFinite(userScale)?Math.clamp(userScale,.65,1.4):1;
        double density=Math.min(1.75*preference,Math.min(framebufferWidth/320.0,framebufferHeight/240.0));
        density=Math.max(.25,density);
        return new GuiViewport((int)Math.floor(framebufferWidth/density),(int)Math.floor(framebufferHeight/density),(float)(density/minecraftScale));
    }
    double toLogical(double coordinate){return coordinate/renderScale;}
}
