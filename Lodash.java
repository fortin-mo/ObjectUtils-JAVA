/**
 * @Author mooffy
 */

import org.apache.commons.lang3.StringUtils;

import javax.el.MethodNotFoundException;
import java.io.InvalidClassException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lodash {

  private static final String METHOD_REGEX = "^[a-zA-Z_]{1}[a-zA-Z_0-9]*\\({1}(\\{[0-9]+\\},{0,1})*\\){1}$";
  private static final String METHOD_ARGUMENT_REGEX = "\\{[0-9]+\\}";
  private static final String METHOD_ITERABLE_INDEX = "^\\[{1}-{0,1}[0-9]*\\]{1}$";


  public static Object get(Object object, List<String> path, Object... args) {
    if (path == null || path.isEmpty()) {
      return object;
    }
    if (args == null)
      args = new Object[0];

    return get(object, path.toArray(new String[path.size()]), args);
  }

  /**
   * Lodash equivalent of get
   *
   * example :
   *  get(myObject, Arrays.asList("getName()", "toUpperCase()", "substring({0},{1})", 0, 6);
   *  // will get the name, then upper case all chars, then subsstring from 0 to 6.
   *
   * returns null if path not found
   *
   * @param object object to get from
   * @param path array of path (methods, field) -- e.g "get({0})", "name", "removeFrom({1},{2})"
   * @param args arguments to invoke methods with
   * @return object returned from the last path
   */
  public static Object get(Object object, String[] path, Object... args) {
    if (args == null) {
      args = new Object[0];
    }
      
    Object returned = object;

    try {
      for (String property : path) {

        if (property.matches(METHOD_REGEX)) {
          // method call
          String arguments = property.substring(property.indexOf("(") + 1, property.length() - 1);
          String methodName = property.substring(0, property.indexOf("("));
          String[] _args;

          if (StringUtils.isBlank(arguments)) {
            _args = new String[0];
          } else {
            Pattern p = Pattern.compile(METHOD_ARGUMENT_REGEX);
            Matcher m = p.matcher(arguments.replaceAll(",", StringUtils.EMPTY));

            List<String> l = new ArrayList<>();
            while(m.find()) {
              l.add(m.group(0));
            }

            _args = l.toArray(new String[l.size()]);
          }

          Method method = null;
          Object[] invokeWith = new Object[_args.length];
          Class[] parameterTypes = new Class[_args.length];

          int paramIndex = 0;
          for (String param : _args) {
            int ind = Integer.parseInt(param.substring(1, param.length() - 1));
            parameterTypes[paramIndex] = args[ind].getClass();
            invokeWith[paramIndex] = args[ind];
            paramIndex++;
          }

          Method[] methods = returned.getClass().getDeclaredMethods();

          if (parameterTypes.length <= 0) {
            method = returned.getClass().getDeclaredMethod(methodName, null);
          } else {
            for(Method m : methods) {
              if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {

                Boolean same = true;

                Class[] params = m.getParameterTypes();
                for (int i = 0; i < params.length && same; i++) {
                  same = compareTypes(parameterTypes[i], params[i]);
                }

                if (same) {
                  method = m;
                  break;
                }
              }
            }
          }

          if (method == null) {
            throw new MethodNotFoundException();
          }

          returned = method.invoke(returned, invokeWith);

        } else if (property.matches(METHOD_ITERABLE_INDEX)) {
          int index = Integer.parseInt(property.substring(1, property.length() - 1));

          if (index < 0) {
            throw new IndexOutOfBoundsException();
          }

          if (object.getClass().isArray()) {
            returned = Array.get(returned, index);
          } else if (returned instanceof Iterable){
            int i = 0;

            Iterator it = ((Iterable)returned).iterator();

            Object found = null;

            while(it.hasNext() && i <= index){
              Object next = it.next();
              if (i == index) {
                found = next;
              }
              i++;
            }

            if (found == null) {
              throw new IndexOutOfBoundsException();
            }

            returned = found;
          } else {
            throw new InvalidClassException("Iterable");
          }
        } else {
          // field
          returned = returned.getClass().getField(property).get(returned);
        }
      }
    } catch (Exception ex) {
      // returns null if any exception is caught
      returned = null;
    }

    return returned;
  }

  /**
   * compare to classes.. usefull for comparing primitive types
   * @param a first
   * @param b second
   * @return boolean
   */
  private static boolean compareTypes (Class a, Class b) {
    if (a == null || b == null) {
      return false;
    }

    switch (a.getName()) {
      case "java.lang.Boolean":
      case "boolean":
        return b.getName().equals("java.lang.Boolean") || b.getName().equals("boolean");

      case "java.lang.Byte":
      case "byte":
        return b.getName().equals("java.lang.Byte") || b.getName().equals("byte");

      case "java.lang.Character":
      case "char":
        return b.getName().equals("java.lang.Character") || b.getName().equals("char");

      case "java.lang.Short":
      case "short":
        return b.getName().equals("java.lang.Short") || b.getName().equals("short");

      case "java.lang.Integer":
      case "int":
        return b.getName().equals("java.lang.Integer") || b.getName().equals("int");

      case "java.lang.Long":
      case "long":
        return b.getName().equals("java.lang.Long") || b.getName().equals("long");

      case "java.lang.Float":
      case "float":
        return b.getName().equals("java.lang.Float") || b.getName().equals("float");

      case "java.lang.Double":
      case "double":
        return b.getName().equals("java.lang.Double") || b.getName().equals("double");

      default:
        return a.equals(b);
    }
  }
}
