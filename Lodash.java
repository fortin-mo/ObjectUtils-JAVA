/**
 * @Author mooffy
 */

// package;

import com.google.common.collect.Iterables;

import java.io.InvalidClassException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.el.MethodNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class Lodash {

  private static final String METHOD_REGEX = "^[a-zA-Z_]{1}[a-zA-Z_0-9]*\\({1}(\\{[0-9]+\\},{0,1})*\\){1}$";
  private static final String METHOD_ARGUMENT_REGEX = "\\{[0-9]+\\}";
  private static final String METHOD_ITERABLE_INDEX = "^\\[{1}-{0,1}[0-9]*\\]{1}$";

  /*
   *
   * example :
   *  get(myObject, Arrays.asList("getName()", "toUpperCase()", "substring({0},{1})", 0, 6);
   *  // will get the name, then upper case all chars, then substring from 0 to 6.
   *
   */

  /**
   * Generic implement of Lodash get with default value.
   * returns def if null
   *
   * @param object object to get from
   * @param def default value
   * @param path array of path (methods, field) -- e.g "get({0})", "name", "removeFrom({1},{2})"
   * @param args arguments to invoke methods with
   * @param <T> return type
   * @return object returned from path of type T, or null
   */
  public static <T> T getOrDefault(Object object, T def, List<String> path, Object... args) {
    // Assert.notNull(def, "def Arguement cannot be null");

    Object value = get(object, path, args);

    if (Objects.isNull(value)) {
      return def;
    }

    return (T)value;
  }

  /**
   * Generic implement of Lodash get.
   * Returns null if not found.
   *
   * @param object object to get from
   * @param type return type
   * @param path array of path (methods, field) -- e.g "get({0})", "name", "removeFrom({1},{2})"
   * @param args arguments to invoke methods with
   * @param <T> return type
   * @return object returned from path of type T, or null
   */
  public static <T> T get(Object object, Class<T> type, List<String> path, Object... args) {
    Assert.notNull(type, "type Argument is required");

    Object value = get(object, path, args);

    if (Objects.isNull(value)) {
      return null;
    }

    return type.cast(value);
  }

  /**
   * Generic implement of Lodash get.
   * Returns null if not found.
   *
   * @param object object to get from
   * @param path array of path (methods, field) -- e.g "get({0})", "name", "removeFrom({1},{2})"
   * @param args arguments to invoke methods with
   * @return object returned from path, or null
   */
  public static Object get(Object object, List<String> path, Object... args) {
    if (CollectionUtils.isEmpty(path)) {
      return object;
    }

    return get(object, path.toArray(new String[path.size()]), args);
  }

  /**
   * Lodash equivalent of get.
   * returns null if path not found.
   *
   * @param object object to get from
   * @param path array of path (methods, field) -- e.g "get({0})", "name", "removeFrom({1},{2})"
   * @param args arguments to invoke methods with
   * @return object returned from path, or null
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
          String unparsedArguments = property.substring(property.indexOf("(") + 1, property.length() - 1);
          String methodName = property.substring(0, property.indexOf("("));
          String[] parsedArguments = parseStringArguments(unparsedArguments);

          Object[] invokeWith = new Object[parsedArguments.length];
          Class[] parameterTypes = new Class[parsedArguments.length];
          Object[] parameterValues = new Object[parsedArguments.length];

          int paramIndex = 0;
          for (String param : parsedArguments) {
            int ind = Integer.parseInt(param.substring(1, param.length() - 1));
            parameterTypes[paramIndex] = args[ind].getClass();
            parameterValues[paramIndex] = args[ind];
            invokeWith[paramIndex] = args[ind];
            paramIndex++;
          }

          Method method = findMethod(returned, methodName, parameterTypes, parameterValues);
          returned = method.invoke(returned, invokeWith);

        } else if (property.matches(METHOD_ITERABLE_INDEX)) {
          int index = Integer.parseInt(property.substring(1, property.length() - 1));

          if (index < 0) {
            throw new IndexOutOfBoundsException();
          }

          if (object.getClass().isArray()) {
            returned = Array.get(returned, index);
          } else if (returned instanceof Iterable) {
            Iterable it = ((Iterable<Object>)returned);
            returned = Iterables.get(it, index);
          } else {
            throw new InvalidClassException("Expected Iterable");
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

  private static String[] parseStringArguments(String unparsedArguments) {
    String[] parsedArguments;

    if (StringUtils.isBlank(unparsedArguments)) {
      parsedArguments = new String[0];
    } else {
      Pattern pattern = Pattern.compile(METHOD_ARGUMENT_REGEX);
      Matcher matcher = pattern.matcher(unparsedArguments.replaceAll(",", StringUtils.EMPTY));

      List<String> groups = new ArrayList<>();
      while (matcher.find()) {
        groups.add(matcher.group(0));
      }

      parsedArguments = groups.toArray(new String[groups.size()]);
    }

    return parsedArguments;
  }

  private static Method findMethod(Object obj, String name, Class[] parameterTypes, Object[] parameterValues)
      throws NoSuchMethodException, MethodNotFoundException {

    Method[] methods = obj.getClass().getDeclaredMethods();

    Method method = null;

    if (parameterTypes.length <= 0) {
      method = obj.getClass().getDeclaredMethod(name, null);
    } else {
      for (Method m : methods) {
        if (m.getName().equals(name) && m.getParameterCount() == parameterValues.length) {

          Boolean same = true;

          Class[] params = m.getParameterTypes();
          for (int index = 0; index < params.length && same; index++) {
            same = compareTypes(params[index], parameterTypes[index], parameterValues[index]);
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

    return method;
  }

  /**
   * compare to classes.. usefull for comparing primitive types.
   *
   * @param wanted first
   * @param actual second
   * @return boolean
   */
  private static boolean compareTypes(Class wanted, Class actual, Object actualObject) {
    if (wanted == null || actual == null || actualObject == null) {
      return false;
    }

    if (Object.class.equals(actual)) {
      // Master Super Class
      return true;
    }

    if (wanted.isInstance(actualObject)) {
      return true;
    }

    boolean matching = false;

    switch (wanted.getName()) {
      case "java.lang.Boolean":
      case "boolean":
        matching =  actual.getName().equals("java.lang.Boolean") || actual.getName().equals("boolean");
        break;

      case "java.lang.Byte":
      case "byte":
        matching =  actual.getName().equals("java.lang.Byte") || actual.getName().equals("byte");
        break;

      case "java.lang.Character":
      case "char":
        matching =  actual.getName().equals("java.lang.Character") || actual.getName().equals("char");
        break;

      case "java.lang.Short":
      case "short":
        matching =  actual.getName().equals("java.lang.Short") || actual.getName().equals("short");
        break;

      case "java.lang.Integer":
      case "int":
        matching =  actual.getName().equals("java.lang.Integer") || actual.getName().equals("int");
        break;

      case "java.lang.Long":
      case "long":
        matching =  actual.getName().equals("java.lang.Long") || actual.getName().equals("long");
        break;

      case "java.lang.Float":
      case "float":
        matching =  actual.getName().equals("java.lang.Float") || actual.getName().equals("float");
        break;

      case "java.lang.Double":
      case "double":
        matching =  actual.getName().equals("java.lang.Double") || actual.getName().equals("double");
        break;

      default:
        matching = wanted.equals(actual);
        break;
    }

    return matching;
  }
}
