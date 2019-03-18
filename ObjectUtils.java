/**
 * @Author mooffy
 */

// package;

import com.google.common.collect.Iterables;
import com.sun.javaws.exceptions.InvalidArgumentException;

import java.io.InvalidClassException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.el.MethodNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class ObjectUtils {

  private static final String BRACKET_START = "[";
  private static final String BRACKET_END = "]";

  private static final String METHOD_REGEX = "^[a-zA-Z_]{1}[a-zA-Z_0-9]*\\({1}(\\{[0-9]+\\},{0,1})*\\){1}$";
  private static final String METHOD_ARGUMENT_REGEX = "\\{[0-9]+\\}";
  private static final String METHOD_ITERABLE_INDEX = "^\\[{1}-{0,1}[0-9]*\\]{1}$";
  private static final String WITH_ITERABLE = "\\[{1}-{0,1}[0-9]*\\]{1}";

  private static final String FULL_STRING_REGEX = "(\\.([a-zA-Z_]{1}[a-zA-Z_0-9]*((\\({1}(\\{[0-9]+\\}{0,1})*\\){1})|(\\[{1}-{0,1}[0-9]*\\]{1})){0,1}))+$";

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
   * Lodash equivalent of get.
   * returns null if path not found.
   *
   * @param object object to get from
   * @param path path as string
   * @param args arguments to invoke methods with
   * @return object returned from path, or null
   */
  public static Object get(Object object, String path, Object... args) {

    if (StringUtils.isBlank(path) || object == null) {
      return object;
    }

    PathBuilder builder = new PathBuilder(path);
    return get(object, builder, args);
  }

  /**
   * Lodash equivalent of get.
   * returns null if path not found.
   *
   * @param object object to get from
   * @param paths array of path (methods, field) -- e.g "get({0})", "name", "removeFrom({1},{2})"
   * @param args arguments to invoke methods with
   * @return object returned from path, or null
   */
  public static Object get(Object object, String[] paths, Object... args) {
    if (object == null || paths == null) {
      return object;
    }

    return get(object, Arrays.asList(paths), args);
  }

  /**
   * Generic implement of Lodash get.
   * Returns null if not found.
   *
   * @param object object to get from
   * @param paths array of path (methods, field) -- e.g "get({0})", "name", "removeFrom({1},{2})"
   * @param args arguments to invoke methods with
   * @return object returned from path, or null
   */
  public static Object get(Object object, Collection<String> paths, Object... args) {
    if (CollectionUtils.isEmpty(paths) || object == null) {
      return object;
    }

    PathBuilder pathBuilder = new PathBuilder(paths);
    return get(object, pathBuilder, args);
  }

  private static Object get(Object object, PathBuilder builder, Object... args) {
    if (args == null) {
      args = new Object[0];
    }

    Object returned = object;

    try {
      for (Path path : builder.get()) {

        if(path.isMethod) {

          Class[] parameterTypes = new Class[path.argsIndexes.length];
          Object[] parameterValues = new Object[path.argsIndexes.length];

          int paramIndex = 0;
          for (int ind : path.argsIndexes) {
            Object arg = args[ind];

            parameterTypes[paramIndex] = arg != null ? arg.getClass() : null;
            parameterValues[paramIndex] = args[ind];

            paramIndex++;
          }

          Method method = findMethod(returned, path.name, parameterTypes, parameterValues);
          returned = method.invoke(returned, parameterValues);

        } else if (path.isIterable) {
          if (path.index < 0) {
            throw new IndexOutOfBoundsException();
          }

          if (object.getClass().isArray()) {
            returned = Array.get(returned, path.index);
          } else if (returned instanceof Iterable) {
            Iterable it = ((Iterable<Object>)returned);
            returned = Iterables.get(it, path.index);
          } else {
            throw new InvalidClassException("Expected Iterable");
          }
        } else {
          // isField
          returned = returned.getClass().getField(path.name).get(returned);
        }
      }
    } catch (Exception ex) {
      // returns null if any exception is caught
      returned = null;
    }

    return returned;
  }

  private static Method findMethod(Object obj, String name, Class[] parameterTypes, Object[] parameterValues)
      throws NoSuchMethodException, MethodNotFoundException {

    Method method = null;

    if (parameterTypes.length <= 0) {
      method = obj.getClass().getDeclaredMethod(name, null);
    } else {
      Method[] methods = obj.getClass().getDeclaredMethods();

      List<Method> matches = Arrays.asList(methods).stream().filter(m -> {
        if (m.getName().equals(name) && m.getParameterCount() == parameterValues.length) {

          Boolean same = true;

          Class[] params = m.getParameterTypes();
          for (int index = 0; index < params.length && same; index++) {
            same = compareTypes(params[index], parameterTypes[index], parameterValues[index]);
          }

          return same;
        }

        return false;
      }).collect(Collectors.toList());

      if (!matches.isEmpty()) {
        method = matches.get(0);

        if (matches.size() > 2) {
          System.out.println("Found two or more matching methods with required arguments !");
        }
      }
    }

    if (method == null) {
      throw new MethodNotFoundException();
    }

    return method;
  }

  /**
   * compare two classes.
   *
   * @param wanted wanted class
   * @param actual provided class
   * @param actualObject actual object
   * @return boolean
   */
  private static boolean compareTypes(Class wanted, Class actual, Object actualObject) {
    if (wanted == null) {
      return false;
    }

    if (!wanted.isPrimitive() && (actual == null || actualObject == null)) {
      // primitive classes cannot be instantiate with NULL
      return true;
    }

    if (Object.class.equals(actual)) {
      // Master Super Class
      return true;
    }

    if (wanted.isInstance(actualObject)) {
      return true;
    }

    boolean matching;

    // handles primitive types
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

  public static class PathBuilder {
    List<Path> paths;

    public PathBuilder(String path) {
      this.paths = new ArrayList<>();

      if (StringUtils.isBlank(path)) {
        return;
      }

      if (!path.contains(".")) {
        paths.add(new Path(path));
      } else {
        buildPaths(Arrays.asList(path.split("\\.")));
      }
    }

    public PathBuilder(Collection<String> properties) {
      buildPaths(properties);
    }

    public List<Path> get() {
      return this.paths;
    }

    private void buildPaths(Collection<String> properties) {
      this.paths = new ArrayList<>();

      if (CollectionUtils.isEmpty(properties)) {
        return;
      }

      for(String p : properties) {
        if (StringUtils.isBlank(p)) {
          // skip empty
          continue;
        }

        Pattern pattern = Pattern.compile(WITH_ITERABLE);
        if (!p.matches(METHOD_ITERABLE_INDEX) && pattern.matcher(p).find(1)) {
          // ex: .split({0})[1] --> .split({0}) and [1] separately

          Matcher matcher = pattern.matcher(p);

          int lastKnownStartBracket = p.indexOf(BRACKET_START);

          String tempPath = p.substring(0, lastKnownStartBracket);
          paths.add(new Path(tempPath));

          boolean skippedFirstGroup = false;
          while (matcher.find()) {

            if (skippedFirstGroup) {
              int endBracketIndex = p.indexOf(BRACKET_END, lastKnownStartBracket);
              int nextStartBracketIndex = p.indexOf(BRACKET_START, endBracketIndex);

              lastKnownStartBracket = nextStartBracketIndex;

              if (endBracketIndex >= 0 && nextStartBracketIndex > endBracketIndex) {
                tempPath = p.substring(endBracketIndex + 1, nextStartBracketIndex);

                if (tempPath.length() > 0) {
                  paths.add(new Path(tempPath));
                }
              }
            }

            skippedFirstGroup = true;

            paths.add(new Path(matcher.group(0)));
          }

        } else {
          paths.add(new Path(p));
        }
      }
    }
  }

  public static class Path {
    boolean isIterable = false;
    boolean isField = false;
    boolean isMethod = false;

    String path;
    String name;

    String[] args;
    int[] argsIndexes;
    int index;

    public Path(String path) {
      this.path = path;

      if (path.matches(METHOD_ITERABLE_INDEX)) {
        buildIterable();
      } else if (path.matches(METHOD_REGEX)) {
        buildMethod();
      } else { // is field
        buildField();
      }
    }

    private void buildField() {
      isField = true;
      name = path;
    }

    private void buildMethod() {
      isMethod = true;

      String unparsedArguments = path.substring(path.indexOf("(") + 1, path.length() - 1);
      args = parseStringArguments(unparsedArguments);

      argsIndexes = new int[args.length];

      for (int index = 0; index < args.length; index++) {
        String arg = args[index];
        int ind = Integer.parseInt(arg.substring(1, arg.length() - 1));
        argsIndexes[index] = ind;
      }

      name = path.substring(0, path.indexOf("("));
    }

    private void buildIterable() {
      isIterable = true;
      index = Integer.parseInt(path.substring(1, path.length() - 1));
    }

    private String[] parseStringArguments(String unparsedArguments) {
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
  }
}
