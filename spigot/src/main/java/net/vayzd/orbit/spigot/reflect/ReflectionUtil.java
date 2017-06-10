/*
 * This file is part of Orbit, licenced under the MIT Licence (MIT)
 *
 * Copyright (c) Vayzd Network <https://www.vayzd.net/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.vayzd.orbit.spigot.reflect;

import lombok.*;
import org.bukkit.*;

import java.lang.reflect.*;

import static com.google.common.base.Preconditions.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@SuppressWarnings("ResultOfMethodCallIgnored")
public class ReflectionUtil {

    private static String versionString;

    public static Class<?> getClassFromNMS(String className) throws ClassNotFoundException {
        checkNotNull(className);
        return Class.forName(String.format("net.minecraft.server.%s.%s", versionString, className));
    }

    public static Class<?> getClassFromOBC(String className) throws ClassNotFoundException {
        checkNotNull(className);
        return Class.forName(String.format("org.bukkit.craftbukkit.%s.%s", versionString, className));
    }

    public static Field getAccessibleField(Class<?> fromClass, String fieldName) throws NoSuchFieldException {
        checkNotNull(fromClass);
        checkNotNull(fieldName);
        Field field = fromClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    static {
        String[] packageComponentArray = Bukkit.getServer().getClass().getPackage().getName()
                .replace(".", ",")
                .split(",");
        versionString = packageComponentArray[3];
    }
}
