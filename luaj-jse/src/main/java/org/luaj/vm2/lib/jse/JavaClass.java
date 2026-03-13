/*******************************************************************************
 * Copyright (c) 2011 Luaj.org. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 ******************************************************************************/
package org.luaj.vm2.lib.jse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.mapping.TinyParser;

/**
 * LuaValue that represents a Java class.
 *
 * <p>Will respond to get() and set() by returning field values, or java methods.
 *
 * <p>This class is not used directly. It is returned by calls to {@link
 * CoerceJavaToLua#coerce(Object)} when a Class is supplied.
 *
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
public class JavaClass extends JavaInstance<Class<?>> implements CoerceJavaToLua.Coercion {

  static final Map<Class<?>, JavaClass> classes = Collections.synchronizedMap(new HashMap<>());

  static final LuaValue NEW = valueOf("new");

  public static TinyParser parser;

  Map<LuaString, Field> fields;
  Map<LuaString, LuaValue> methods;
  Map<LuaString, Class<?>> innerClasses;

  public static JavaClass forClass(Class<?> c) {
    JavaClass j = classes.get(c);
    if (j == null) classes.put(c, j = new JavaClass(c));
    return j;
  }

  protected JavaClass(Class<?> c) {
    super(c);
    this.jClass = this;
  }

  @Override
  public LuaValue coerce(Object javaValue) {
    return this;
  }

  Field getField(LuaValue key) {
    if (fields == null) {
      fields = new HashMap<>();

			for (Field fi : m_instance.getFields()) {
				try {
					fi.setAccessible(true);
				} catch (SecurityException ignored) {
					continue;
				}
				fields.put(LuaValue.valueOf(getFieldName(m_instance, fi)), fi);
			}
    }
    return fields.get(key.checkstring());
  }

  LuaValue getMethod(LuaValue key) {
    if (methods == null) {
      methods = new HashMap<>();

      Map<String, List<JavaMethod>> namedMethods = new HashMap<>();
      for (Method mi : m_instance.getMethods()) {
				try{
					mi.setAccessible(true);
				} catch (SecurityException e) {
					continue;
				}
        namedMethods
            .computeIfAbsent(getMethodName(m_instance, mi), k -> new ArrayList<>())
            .add(JavaMethod.forMethod(mi));
      }

      namedMethods.forEach(
          (name, list) ->
              methods.put(
                  LuaValue.valueOf(name),
                  list.size() == 1
                      ? list.getFirst()
                      : JavaMethod.forMethods(list.toArray(new JavaMethod[0]))));

			List<JavaConstructor> constructors = new ArrayList<>();
			for (Constructor<?> c : m_instance.getDeclaredConstructors()) {
				try {
					c.setAccessible(true);
					constructors.add(JavaConstructor.forConstructor(c));
				} catch (SecurityException ignored) {}
			}


			switch (constructors.size()) {
        case 0 -> {}
        case 1 -> methods.put(NEW.checkstring(), constructors.getFirst());
        default ->
            methods.put(
                NEW.checkstring(),
                JavaConstructor.forConstructors(constructors.toArray(new JavaConstructor[0])));
      }
    }
    return methods.get(key.checkstring());
  }

  Class<?> getInnerClass(LuaValue key) {
    if (innerClasses == null) {
      Map<LuaString, Class<?>> m = new HashMap<>();
      Class<?>[] c = m_instance.getDeclaredClasses();
      for (Class<?> ci : c) {
        String name = getInnerClassName(ci);
        String stub = name.substring(Math.max(name.lastIndexOf('$'), name.lastIndexOf('.')) + 1);
        m.put(LuaValue.valueOf(stub), ci);
      }
      innerClasses = m;
    }
    return innerClasses.get(key.checkstring());
  }

  private TinyParser.ClassMapping mappingFor(Class<?> cls) {
    if (parser == null) return null;
    return parser.classMappings().get(cls.getName());
  }

  protected String getFieldName(Class<?> cls, Field fi) {
    TinyParser.ClassMapping classMapping = mappingFor(cls);
    if (classMapping != null) {
      TinyParser.MemberMapping mapping = classMapping.fields().get(fi.getName());
			System.out.println(mapping);
			if (mapping != null) return mapping.named();
    }
		System.out.println("fail" + fi);
		return fi.getName();
  }

  protected String getMethodName(Class<?> cls, Method mi) {
    TinyParser.ClassMapping classMapping = mappingFor(cls);
    if (classMapping != null) {
      TinyParser.MemberMapping mapping = classMapping.methods().get(mi.getName());
			System.out.println(mapping);

			if (mapping != null) return mapping.named();
    }
		System.out.println("fail" + mi);

		return mi.getName();
  }

  protected static String getInnerClassName(Class<?> ci) {
    if (parser != null) {
      TinyParser.ClassMapping mapping = parser.classMappings().get(ci.getName());
      System.out.println(mapping);
      if (mapping != null) return mapping.named();
    }
    System.out.println("fail" + ci);
    return ci.getName();
  }

  public LuaValue getConstructor() {
    return getMethod(NEW);
  }
}
