package snd.module;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.tann.dice.util.listener.TannListener;
import com.tann.dice.util.ui.standardButton.StandardButton;

import snd.contracts.SndLog;

/**
 * What the game's anonymous listeners and runnables carry: the variables
 * they captured, found by type (never by the compiler's generated names), and
 * the game method that built them. Many of the game's controls keep their
 * domain object nowhere else. Every lookup is cached per class, so readers
 * built every frame pay a map hit, not a reflective scan.
 */
public final class Captured {
    private Captured() {
    }

    private static final Field NO_FIELD;
    private static final Method NO_METHOD;
    static {
        try {
            NO_FIELD = Captured.class.getDeclaredField("NO_FIELD");
            NO_METHOD = Captured.class.getDeclaredMethod("noMethod");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void noMethod() {
    }

    private static final Map<Class<?>, Map<Class<?>, Field>> BY_TYPE = new HashMap<Class<?>, Map<Class<?>, Field>>();
    private static final Map<Class<?>, Method> ENCLOSING = new HashMap<Class<?>, Method>();
    private static final Map<Class<?>, Map<String, Field>> NAMED = new HashMap<Class<?>, Map<String, Field>>();

    /** The holder's captured variable of this type (its class's first field assignable to it), or null. */
    public static <T> T value(Object holder, Class<T> type) {
        if (holder == null) {
            return null;
        }
        Class<?> cls = holder.getClass();
        Map<Class<?>, Field> fields = BY_TYPE.get(cls);
        if (fields == null) {
            fields = new HashMap<Class<?>, Field>();
            BY_TYPE.put(cls, fields);
        }
        Field field = fields.get(type);
        if (field == null) {
            field = NO_FIELD;
            for (Field f : cls.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    field = f;
                    break;
                }
            }
            fields.put(type, field);
        }
        if (field == NO_FIELD) {
            return null;
        }
        try {
            return type.cast(field.get(holder));
        } catch (IllegalAccessException e) {
            SndLog.error("captured " + type.getSimpleName() + " read failed on " + cls.getName(), e);
            return null;
        }
    }

    /** The holder's captured primitive of this type (int.class, boolean.class), boxed, or null. */
    public static Object primitive(Object holder, Class<?> type) {
        if (holder == null || !type.isPrimitive()) {
            return null;
        }
        Class<?> cls = holder.getClass();
        Map<Class<?>, Field> fields = BY_TYPE.get(cls);
        if (fields == null) {
            fields = new HashMap<Class<?>, Field>();
            BY_TYPE.put(cls, fields);
        }
        Field field = fields.get(type);
        if (field == null) {
            field = NO_FIELD;
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == type) {
                    f.setAccessible(true);
                    field = f;
                    break;
                }
            }
            fields.put(type, field);
        }
        if (field == NO_FIELD) {
            return null;
        }
        try {
            return field.get(holder);
        } catch (IllegalAccessException e) {
            SndLog.error("captured " + type.getSimpleName() + " read failed on " + cls.getName(), e);
            return null;
        }
    }

    /** Whether the holder's class was declared inside the game method owner.method. */
    public static boolean builtBy(Object holder, Class<?> owner, String method) {
        if (holder == null) {
            return false;
        }
        Class<?> cls = holder.getClass();
        Method enclosing = ENCLOSING.get(cls);
        if (enclosing == null) {
            enclosing = cls.getEnclosingMethod();
            if (enclosing == null) {
                enclosing = NO_METHOD;
            }
            ENCLOSING.put(cls, enclosing);
        }
        return enclosing != NO_METHOD && enclosing.getDeclaringClass() == owner
                && enclosing.getName().equals(method);
    }

    /** The actor's game listener built in owner.method, or null. */
    public static TannListener listenerBuiltBy(Actor actor, Class<?> owner, String method) {
        for (EventListener listener : actor.getListeners()) {
            if (listener instanceof TannListener && builtBy(listener, owner, method)) {
                return (TannListener) listener;
            }
        }
        return null;
    }

    /** A variable one of the actor's game listeners captured, or null. */
    public static <T> T byListener(Actor actor, Class<T> type) {
        for (EventListener listener : actor.getListeners()) {
            if (listener instanceof TannListener) {
                T found = value(listener, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** What a game button runs when pressed (its package-private runnable), or null. */
    public static Runnable runnable(StandardButton button) {
        return (Runnable) field(button, StandardButton.class, "runnable");
    }

    /**
     * A named field the game keeps out of reach (private or package-private),
     * declared on owner. A missing field is a game change: logged once, null.
     */
    public static Object field(Object obj, Class<?> owner, String name) {
        Map<String, Field> fields = NAMED.get(owner);
        if (fields == null) {
            fields = new HashMap<String, Field>();
            NAMED.put(owner, fields);
        }
        Field field = fields.get(name);
        if (field == null) {
            try {
                field = owner.getDeclaredField(name);
                field.setAccessible(true);
            } catch (NoSuchFieldException e) {
                SndLog.error("field " + owner.getSimpleName() + "." + name + " is gone", e);
                field = NO_FIELD;
            }
            fields.put(name, field);
        }
        if (field == NO_FIELD) {
            return null;
        }
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            SndLog.error("field " + owner.getSimpleName() + "." + name + " read failed", e);
            return null;
        }
    }
}
