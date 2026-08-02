package snd.core.graph;

/**
 * The four navigable directions between graph nodes (explicit edges). Tab-stop
 * cycling and region jumps are OPERATIONS over node metadata (stopKey /
 * regionKey), not edges — they carry per-stop remembered positions, which a
 * static edge can't express.
 */
public enum GraphDir {
    UP,
    RIGHT,
    DOWN,
    LEFT
}
