package snd.host;

import java.lang.instrument.Instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import snd.core.SndLog;

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
                .installOn(inst);
        SndLog.info("frame-pump hook installed on com.tann.dice.Main.render");
    }
}
