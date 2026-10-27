# Buffers

Buffers are review lists for extra information. The buffer keys let you read them without moving focus away.

## Keys

- Ctrl+Right: next buffer. Ctrl+Left: previous buffer.
- Ctrl+Up: next line. Ctrl+Down: previous line.

Switching buffers speaks the buffer's name and its current line. Moving focus starts every buffer over and puts you back in the control buffer. Buffers with nothing in them are skipped.

## The Buffers

In cycling order:

| Buffer | Holds |
| --- | --- |
| control | The focused control's line, then one line per tooltip: keyword rules, statuses in full |
| hero, monster | The whole unit the control concerns: hp, targets, all six sides, keyword rules, statuses |
| items | What that hero carries, or the focused item: name and tier, then its description |
| party, enemies | Everyone on a side, one line each, with hp, shields and incoming damage |
| log | What happened: banners, rolls, outcomes, phases, notifications |
