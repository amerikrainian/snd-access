package snd.core;

import snd.core.speech.SpeechPipeline;

/** What the permanent host offers the reloadable module. */
public interface HostServices {
    SpeechPipeline speech();

    /** The module load generation (1 on first load, +1 per reload). */
    int generation();
}
