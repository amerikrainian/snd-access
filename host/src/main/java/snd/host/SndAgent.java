package snd.host;

import java.lang.instrument.Instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import snd.contracts.SndLog;

/**
 * The -javaagent entry point. Installs the frame-pump hook before any game
 * class loads, then boots the host. Works both via premain (normal launch) and
 * agentmain (late attach during development).
 */
public final class SndAgent {
    private SndAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        start(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(args, inst);
    }

    private static void start(String args, Instrumentation inst) {
        SndLog.info("agent starting on java " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vm.name") + ")");
        try {
            installHooks(inst);
        } catch (Throwable t) {
            SndLog.error("failed to install hooks; the mod will be inert", t);
            return;
        }
        try {
            new Host().boot();
        } catch (Throwable t) {
            SndLog.error("host boot failed", t);
        }
    }

    private static void installHooks(Instrumentation inst) {
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(ElementMatchers.named("com.tann.dice.Main"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            JavaModule module,
                                                            java.security.ProtectionDomain protectionDomain) {
                        return builder.visit(net.bytebuddy.asm.Advice.to(FrameAdvice.class)
                                .on(ElementMatchers.named("render").and(ElementMatchers.takesArguments(0))));
                    }
                })
                .type(ElementMatchers.named("com.tann.dice.gameplay.effect.targetable.ability.ui.AbilityHolder"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            JavaModule module,
                                                            java.security.ProtectionDomain protectionDomain) {
                        return builder.visit(net.bytebuddy.asm.Advice.to(TransientTextAdvice.class)
                                .on(ElementMatchers.named("showInfo")
                                        .and(ElementMatchers.takesArgument(0, String.class))));
                    }
                })
                .type(ElementMatchers.named("com.tann.dice.screens.dungeon.TargetingManager"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            JavaModule module,
                                                            java.security.ProtectionDomain protectionDomain) {
                        return builder.visit(net.bytebuddy.asm.Advice.to(TransientTextAdvice.class)
                                .on(ElementMatchers.named("showError")
                                        .and(ElementMatchers.takesArgument(0, String.class))));
                    }
                })
                // Combat state text ("dodged", "immune") and chatter over the
                // entity panels — the panel's entity names the speaker.
                .type(ElementMatchers.named("com.tann.dice.screens.dungeon.panels.entPanel.EntPanelCombat"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            JavaModule module,
                                                            java.security.ProtectionDomain protectionDomain) {
                        return builder.visit(net.bytebuddy.asm.Advice.to(EntTextAdvice.class)
                                .on(ElementMatchers.named("addMessage").or(ElementMatchers.named("addSpeechBubble"))
                                        .and(ElementMatchers.takesArgument(0, String.class))));
                    }
                })
                // Ability-bar wisps: mana gains, discards, the save-loaded
                // notice. The 2-arg core only (the 1-arg overload delegates).
                .type(ElementMatchers.named("com.tann.dice.gameplay.effect.targetable.ability.ui.AbilityHolder"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            JavaModule module,
                                                            java.security.ProtectionDomain protectionDomain) {
                        return builder.visit(net.bytebuddy.asm.Advice.to(TransientTextAdvice.class)
                                .on(ElementMatchers.named("addWisp")
                                        .and(ElementMatchers.takesArguments(String.class, float.class))));
                    }
                })
                .installOn(inst);
        // The clipboard-copied toast needs no hook: it lands in the popup
        // holder the module already watches.
        SndLog.info("hooks installed: Main.render, AbilityHolder.showInfo+addWisp, "
                + "TargetingManager.showError, EntPanelCombat.addMessage+addSpeechBubble");
    }
}
