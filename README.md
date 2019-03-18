# lodash-java

This is an Object utility class that is inspired by lodash ( JS )
For now, it only implements the `get` and `getOrDefault` function.

Here are some exemple

```java
String myObject = new String("hello world"); // or any other Object

Object value = ObjectUtils.get(myObject, ".toString().split({0})[0].toUpperCase()", " "); // this is the returned object
AssertTrue("HELLO", value);
```
