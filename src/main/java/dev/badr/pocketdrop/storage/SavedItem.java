package dev.badr.pocketdrop.storage;

/** One file the bot saved. */
public record SavedItem(long time, String name, Kind kind, long size, String path) {}
