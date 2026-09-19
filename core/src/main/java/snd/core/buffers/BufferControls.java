package snd.core.buffers;

import snd.contracts.speech.SpeechPipeline;
import snd.core.loc.Loc;

/**
 * The four buffer review commands, composed and spoken here so every screen
 * shares one behavior: switching a buffer speaks its name and current line;
 * stepping speaks just the line — next is further from the buffer's home
 * line (Ctrl+Up), previous back toward it (Ctrl+Down) — and an edge re-reads
 * the current line rather than going silent. Everything interrupts, as an answer to a key press.
 */
public final class BufferControls {
    private final BufferManager buffers;
    private final SpeechPipeline speech;

    public BufferControls(BufferManager buffers, SpeechPipeline speech) {
        this.buffers = buffers;
        this.speech = speech;
    }

    public void nextBuffer() {
        buffers.moveBuffer(1);
        reportBuffer();
    }

    public void previousBuffer() {
        buffers.moveBuffer(-1);
        reportBuffer();
    }

    public void nextLine() {
        Buffer buffer = buffers.current();
        if (buffer != null) {
            buffer.moveNext();
        }
        reportLine(buffer);
    }

    public void previousLine() {
        Buffer buffer = buffers.current();
        if (buffer != null) {
            buffer.movePrevious();
        }
        reportLine(buffer);
    }

    private void reportBuffer() {
        Buffer buffer = buffers.current();
        if (buffer == null) {
            speech.speak(Loc.get("ui", "buffer.none"), true);
            return;
        }
        speech.speak(Loc.get("ui", "buffer.line", "buffer", buffer.label(), "line", buffer.currentLine()), true);
    }

    private void reportLine(Buffer buffer) {
        String line = buffer != null ? buffer.currentLine() : null;
        speech.speak(line != null ? line : Loc.get("ui", "buffer.none"), true);
    }
}
