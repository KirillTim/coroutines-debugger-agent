package mylibrary;

import kotlin.coroutines.experimental.intrinsics.IntrinsicsKt;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ObjectPrinter {

    public static Object isSuspended(Object res) {
        if (res == IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
            System.out.println("SUSPENDED");
        }
        return res;
    }

    private static String objToString(Object obj) {
        String objStr = "<can't be printed>";
        try {
            objStr = "" + obj;
        } catch (Exception ignored) {
        }
        return objStr;
    }

    public static void print(Object obj) {
        System.out.println("print fields of " + objToString(obj) + " with type: " + obj.getClass().getName());
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                String prefix = Modifier.isPrivate(f.getModifiers()) ? "(private) " : "";
                System.out.println(prefix + f.getName() + " : " + objToString(f.get(obj)));
            } catch (IllegalAccessException e) {
                System.out.println("while printing value of " + f.getName() + "exception: " + e.getMessage());
                //e.printStackTrace();
            }
        }
        System.out.println("print getters:");
        for (Method m : obj.getClass().getMethods()) {
            if (m.getName().startsWith("get") && m.getGenericParameterTypes().length == 0) {
                m.setAccessible(true);
                try {
                    System.out.println(m.getName() + ": " + objToString(m.invoke(obj)));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    System.out.println("while invoking " + m.getName() + "exception: " + e.getMessage());
                }
            }
        }
        System.out.println("---------------------------");
    }
}
