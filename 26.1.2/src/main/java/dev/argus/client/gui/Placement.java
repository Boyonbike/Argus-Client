package dev.argus.client.gui;

/** Where a module's tool card goes among its settings cards. */
record Placement(String after, boolean atEnd) {
    /** Before every settings card: for the module's main tool, when the page exists mostly for it. */
    static Placement first() { return new Placement(null, false); }
    /** Directly under the settings group of that name: for a tool that works with those settings. */
    static Placement after(String group) { return new Placement(group, false); }
    /** After every settings card: for data the settings act on, such as saved presets. */
    static Placement last() { return new Placement(null, true); }
}
