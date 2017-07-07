package mylibrary;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ObjectPrinter {
    public static void print(Object obj) {
        System.out.println("print values of "+obj);
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                String prefix = Modifier.isPrivate(f.getModifiers()) ? "(private) " : "";
                System.out.println(prefix + f.getName() + " : " + f.get(obj));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
