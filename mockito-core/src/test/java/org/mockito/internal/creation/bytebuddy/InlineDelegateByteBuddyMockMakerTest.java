/*
 * Copyright (c) 2017 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.creation.bytebuddy;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.utility.JavaConstant;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.creation.bytebuddy.sample.DifferentPackage;
import org.mockito.internal.creation.settings.CreationSettings;
import org.mockito.internal.framework.DisabledMockHandler;
import org.mockito.internal.handler.MockHandlerImpl;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.internal.util.collections.Sets;
import org.mockito.mock.MockCreationSettings;
import org.mockito.mock.SerializableMode;
import org.mockito.plugins.MockMaker;

public class InlineDelegateByteBuddyMockMakerTest
        extends AbstractByteBuddyMockMakerTest<InlineByteBuddyMockMaker> {

    public InlineDelegateByteBuddyMockMakerTest() {
        super(new InlineByteBuddyMockMaker());
    }

    @Override
    protected Class<?> mockTypeOf(Class<?> type) {
        return type;
    }

    @Test
    public void should_create_mock_from_final_class() throws Exception {
        MockCreationSettings<FinalClass> settings = settingsFor(FinalClass.class);
        FinalClass proxy =
                mockMaker.createMock(settings, new MockHandlerImpl<FinalClass>(settings));
        assertThat(proxy.foo()).isEqualTo("bar");
    }

    @Test
    public void should_create_mock_from_final_spy() throws Exception {
        MockCreationSettings<FinalSpy> settings = settingsFor(FinalSpy.class);
        Optional<FinalSpy> proxy =
                mockMaker.createSpy(
                        settings,
                        new MockHandlerImpl<>(settings),
                        new FinalSpy("value", true, (byte) 1, (short) 1, (char) 1, 1, 1L, 1f, 1d));
        assertThat(proxy)
                .hasValueSatisfying(
                        spy -> {
                            assertThat(spy.aString).isEqualTo("value");
                            assertThat(spy.aBoolean).isTrue();
                            assertThat(spy.aByte).isEqualTo((byte) 1);
                            assertThat(spy.aShort).isEqualTo((short) 1);
                            assertThat(spy.aChar).isEqualTo((char) 1);
                            assertThat(spy.anInt).isEqualTo(1);
                            assertThat(spy.aLong).isEqualTo(1L);
                            assertThat(spy.aFloat).isEqualTo(1f);
                            assertThat(spy.aDouble).isEqualTo(1d);
                        });
    }

    @Test
    public void should_create_mock_from_accessible_inner_spy() throws Exception {
        MockCreationSettings<Outer.Inner> settings = settingsFor(Outer.Inner.class);
        Optional<Outer.Inner> proxy =
                mockMaker.createSpy(
                        settings,
                        new MockHandlerImpl<>(settings),
                        new Outer.Inner(new Object(), new Object()));
        assertThat(proxy)
                .hasValueSatisfying(
                        spy -> {
                            assertThat(spy.p1).isNotNull();
                            assertThat(spy.p2).isNotNull();
                        });
    }

    @Test
    public void should_create_mock_from_visible_inner_spy() throws Exception {
        MockCreationSettings<DifferentPackage> settings = settingsFor(DifferentPackage.class);
        Optional<DifferentPackage> proxy =
                mockMaker.createSpy(
                        settings,
                        new MockHandlerImpl<>(settings),
                        new DifferentPackage(new Object(), new Object()));
        assertThat(proxy)
                .hasValueSatisfying(
                        spy -> {
                            assertThat(spy.p1).isNotNull();
                            assertThat(spy.p2).isNotNull();
                        });
    }

    @Test
    public void should_create_mock_from_non_constructable_class() throws Exception {
        MockCreationSettings<NonConstructableClass> settings =
                settingsFor(NonConstructableClass.class);
        NonConstructableClass proxy =
                mockMaker.createMock(
                        settings, new MockHandlerImpl<NonConstructableClass>(settings));
        assertThat(proxy.foo()).isEqualTo("bar");
    }

    @Test
    public void should_create_mock_from_final_class_in_the_JDK() throws Exception {
        MockCreationSettings<Pattern> settings = settingsFor(Pattern.class);
        Pattern proxy = mockMaker.createMock(settings, new MockHandlerImpl<Pattern>(settings));
        assertThat(proxy.pattern()).isEqualTo("bar");
    }

    @Test
    public void should_create_mock_from_abstract_class_with_final_method() throws Exception {
        MockCreationSettings<FinalMethodAbstractType> settings =
                settingsFor(FinalMethodAbstractType.class);
        FinalMethodAbstractType proxy =
                mockMaker.createMock(
                        settings, new MockHandlerImpl<FinalMethodAbstractType>(settings));
        assertThat(proxy.foo()).isEqualTo("bar");
        assertThat(proxy.bar()).isEqualTo("bar");
    }

    @Test
    public void should_create_mock_from_final_class_with_interface_methods() throws Exception {
        MockCreationSettings<FinalMethod> settings =
                settingsFor(FinalMethod.class, SampleInterface.class);
        FinalMethod proxy =
                mockMaker.createMock(settings, new MockHandlerImpl<FinalMethod>(settings));
        assertThat(proxy.foo()).isEqualTo("bar");
        assertThat(((SampleInterface) proxy).bar()).isEqualTo("bar");
    }

    @Test
    public void should_detect_non_overridden_generic_method_of_supertype() throws Exception {
        MockCreationSettings<GenericSubClass> settings = settingsFor(GenericSubClass.class);
        GenericSubClass proxy =
                mockMaker.createMock(settings, new MockHandlerImpl<GenericSubClass>(settings));
        assertThat(proxy.value()).isEqualTo("bar");
    }

    @Test
    public void should_create_mock_from_hashmap() throws Exception {
        MockCreationSettings<HashMap> settings = settingsFor(HashMap.class);
        HashMap proxy = mockMaker.createMock(settings, new MockHandlerImpl<HashMap>(settings));
        assertThat(proxy.get(null)).isEqualTo("bar");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void should_throw_exception_redefining_unmodifiable_class() {
        MockCreationSettings settings = settingsFor(int.class);
        try {
            mockMaker.createMock(settings, new MockHandlerImpl(settings));
            fail("Expected a MockitoException");
        } catch (MockitoException e) {
            e.printStackTrace();
            assertThat(e).hasMessageContaining("Could not modify all classes");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void should_throw_exception_redefining_array() {
        int[] array = new int[5];
        MockCreationSettings<? extends int[]> settings = settingsFor(array.getClass());
        try {
            mockMaker.createMock(settings, new MockHandlerImpl(settings));
            fail("Expected a MockitoException");
        } catch (MockitoException e) {
            assertThat(e).hasMessageContaining("Arrays cannot be mocked");
        }
    }

    @Test
    public void should_create_mock_from_enum() throws Exception {
        MockCreationSettings<EnumClass> settings = settingsFor(EnumClass.class);
        EnumClass proxy = mockMaker.createMock(settings, new MockHandlerImpl<EnumClass>(settings));
        assertThat(proxy.foo()).isEqualTo("bar");
    }

    @Test
    public void should_fail_at_creating_a_mock_of_a_final_class_with_explicit_serialization()
            throws Exception {
        MockCreationSettings<FinalClass> settings =
                new CreationSettings<FinalClass>()
                        .setTypeToMock(FinalClass.class)
                        .setSerializableMode(SerializableMode.BASIC);

        try {
            mockMaker.createMock(settings, new MockHandlerImpl<FinalClass>(settings));
            fail("Expected a MockitoException");
        } catch (MockitoException e) {
            assertThat(e)
                    .hasMessageContaining("Unsupported settings")
                    .hasMessageContaining("serialization")
                    .hasMessageContaining("extra interfaces");
        }
    }

    @Test
    public void should_fail_at_creating_a_mock_of_a_final_class_with_extra_interfaces()
            throws Exception {
        MockCreationSettings<FinalClass> settings =
                new CreationSettings<FinalClass>()
                        .setTypeToMock(FinalClass.class)
                        .setExtraInterfaces(Sets.<Class<?>>newSet(List.class));

        try {
            mockMaker.createMock(settings, new MockHandlerImpl<FinalClass>(settings));
            fail("Expected a MockitoException");
        } catch (MockitoException e) {
            assertThat(e)
                    .hasMessageContaining("Unsupported settings")
                    .hasMessageContaining("serialization")
                    .hasMessageContaining("extra interfaces");
        }
    }

    @Test
    public void should_mock_interface() {
        MockSettingsImpl<Set> mockSettings = new MockSettingsImpl<Set>();
        mockSettings.setTypeToMock(Set.class);
        mockSettings.defaultAnswer(new Returns(10));
        Set<?> proxy = mockMaker.createMock(mockSettings, new MockHandlerImpl<Set>(mockSettings));

        assertThat(proxy.size()).isEqualTo(10);
    }

    @Test
    public void should_mock_interface_to_string() {
        MockSettingsImpl<Set> mockSettings = new MockSettingsImpl<Set>();
        mockSettings.setTypeToMock(Set.class);
        mockSettings.defaultAnswer(new Returns("foo"));
        Set<?> proxy = mockMaker.createMock(mockSettings, new MockHandlerImpl<Set>(mockSettings));

        assertThat(proxy.toString()).isEqualTo("foo");
    }

    /**
     * @see <a href="https://github.com/mockito/mockito/issues/2154">https://github.com/mockito/mockito/issues/2154</a>
     */
    @Test
    public void should_mock_class_to_string() {
        MockSettingsImpl<Object> mockSettings = new MockSettingsImpl<Object>();
        mockSettings.setTypeToMock(Object.class);
        mockSettings.defaultAnswer(new Returns("foo"));
        Object proxy =
                mockMaker.createMock(mockSettings, new MockHandlerImpl<Object>(mockSettings));

        assertThat(proxy.toString()).isEqualTo("foo");
    }

    @Test
    public void should_leave_causing_stack() throws Exception {
        MockSettingsImpl<ExceptionThrowingClass> settings = new MockSettingsImpl<>();
        settings.setTypeToMock(ExceptionThrowingClass.class);
        settings.defaultAnswer(Answers.CALLS_REAL_METHODS);

        Optional<ExceptionThrowingClass> proxy =
                mockMaker.createSpy(
                        settings, new MockHandlerImpl<>(settings), new ExceptionThrowingClass());

        StackTraceElement[] returnedStack =
                assertThrows(IOException.class, () -> proxy.get().throwException()).getStackTrace();

        assertNotNull("Stack trace from mockito expected", returnedStack);

        List<StackTraceElement> exceptionClassElements =
                Arrays.stream(returnedStack)
                        .filter(
                                element ->
                                        element.getClassName()
                                                .equals(ExceptionThrowingClass.class.getName()))
                        .collect(Collectors.toList());
        assertEquals(3, exceptionClassElements.size());
        assertEquals("internalThrowException", exceptionClassElements.get(0).getMethodName());
        assertEquals("internalThrowException", exceptionClassElements.get(1).getMethodName());
        assertEquals("throwException", exceptionClassElements.get(2).getMethodName());
    }

    @Test
    public void should_leave_causing_stack_with_two_spies() throws Exception {
        // given
        MockSettingsImpl<ExceptionThrowingClass> settingsEx = new MockSettingsImpl<>();
        settingsEx.setTypeToMock(ExceptionThrowingClass.class);
        settingsEx.defaultAnswer(Answers.CALLS_REAL_METHODS);
        Optional<ExceptionThrowingClass> proxyEx =
                mockMaker.createSpy(
                        settingsEx,
                        new MockHandlerImpl<>(settingsEx),
                        new ExceptionThrowingClass());

        MockSettingsImpl<WrapperClass> settingsWr = new MockSettingsImpl<>();
        settingsWr.setTypeToMock(WrapperClass.class);
        settingsWr.defaultAnswer(Answers.CALLS_REAL_METHODS);
        Optional<WrapperClass> proxyWr =
                mockMaker.createSpy(
                        settingsWr, new MockHandlerImpl<>(settingsWr), new WrapperClass());

        // when
        IOException ex =
                assertThrows(IOException.class, () -> proxyWr.get().callWrapped(proxyEx.get()));
        List<StackTraceElement> wrapperClassElements =
                Arrays.stream(ex.getStackTrace())
                        .filter(
                                element ->
                                        element.getClassName().equals(WrapperClass.class.getName()))
                        .collect(Collectors.toList());

        // then
        assertEquals(1, wrapperClassElements.size());
        assertEquals("callWrapped", wrapperClassElements.get(0).getMethodName());
    }

    @Test
    public void should_remove_recursive_self_call_from_stack_trace() throws Exception {
        StackTraceElement[] stack =
                new StackTraceElement[] {
                    new StackTraceElement("foo", "", "", -1),
                    new StackTraceElement(SampleInterface.class.getName(), "", "", 15),
                    new StackTraceElement("qux", "", "", -1),
                    new StackTraceElement("bar", "", "", -1),
                    new StackTraceElement(SampleInterface.class.getName(), "", "", 15),
                    new StackTraceElement("baz", "", "", -1)
                };

        Throwable throwable = new Throwable();
        throwable.setStackTrace(stack);
        throwable = MockMethodAdvice.removeRecursiveCalls(throwable, SampleInterface.class);

        assertThat(throwable.getStackTrace())
                .isEqualTo(
                        new StackTraceElement[] {
                            new StackTraceElement("foo", "", "", -1),
                            new StackTraceElement("qux", "", "", -1),
                            new StackTraceElement("bar", "", "", -1),
                            new StackTraceElement(SampleInterface.class.getName(), "", "", 15),
                            new StackTraceElement("baz", "", "", -1)
                        });
    }

    @Test
    public void should_handle_missing_or_inconsistent_stack_trace() {
        Throwable throwable = new Throwable();
        throwable.setStackTrace(new StackTraceElement[0]);
        assertThat(MockMethodAdvice.removeRecursiveCalls(throwable, SampleInterface.class))
                .isSameAs(throwable);
    }

    @Test
    public void should_provide_reason_for_wrapper_class() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(Integer.class);
        assertThat(mockable.nonMockableReason())
                .isEqualTo("Cannot mock wrapper types, String.class or Class.class");
    }

    @Test
    public void should_provide_reason_for_vm_unsupported() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(int[].class);
        assertThat(mockable.nonMockableReason())
                .isEqualTo("VM does not support modification of given type");
    }

    @Test
    public void should_mock_method_of_package_private_class() throws Exception {
        MockCreationSettings<NonPackagePrivateSubClass> settings =
                settingsFor(NonPackagePrivateSubClass.class);
        NonPackagePrivateSubClass proxy =
                mockMaker.createMock(
                        settings, new MockHandlerImpl<NonPackagePrivateSubClass>(settings));
        assertThat(proxy.value()).isEqualTo("bar");
    }

    @Test
    public void is_type_mockable_excludes_String() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(String.class);
        assertThat(mockable.mockable()).isFalse();
        assertThat(mockable.nonMockableReason())
                .contains("Cannot mock wrapper types, String.class or Class.class");
    }

    @Test
    public void is_type_mockable_excludes_Class() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(Class.class);
        assertThat(mockable.mockable()).isFalse();
        assertThat(mockable.nonMockableReason())
                .contains("Cannot mock wrapper types, String.class or Class.class");
    }

    @Test
    public void is_type_mockable_excludes_primitive_classes() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(int.class);
        assertThat(mockable.mockable()).isFalse();
        assertThat(mockable.nonMockableReason()).contains("primitive");
    }

    @Test
    public void is_type_mockable_allows_anonymous() {
        Observer anonymous =
                new Observer() {
                    @Override
                    public void update(Observable o, Object arg) {}
                };
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(anonymous.getClass());
        assertThat(mockable.mockable()).isTrue();
        assertThat(mockable.nonMockableReason()).contains("");
    }

    @Test
    public void is_type_mockable_give_empty_reason_if_type_is_mockable() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(SomeClass.class);
        assertThat(mockable.mockable()).isTrue();
        assertThat(mockable.nonMockableReason()).isEqualTo("");
    }

    @Test
    public void is_type_mockable_give_allow_final_mockable_from_JDK() {
        MockMaker.TypeMockability mockable = mockMaker.isTypeMockable(Pattern.class);
        assertThat(mockable.mockable()).isTrue();
        assertThat(mockable.nonMockableReason()).isEqualTo("");
    }

    @Test
    public void test_parameters_retention() throws Exception {
        Class<?> typeWithParameters =
                new ByteBuddy()
                        .subclass(Object.class)
                        .defineMethod("foo", void.class, Visibility.PUBLIC)
                        .withParameter(String.class, "bar")
                        .intercept(StubMethod.INSTANCE)
                        .make()
                        .load(null)
                        .getLoaded();

        MockCreationSettings<?> settings = settingsFor(typeWithParameters);
        @SuppressWarnings("unchecked")
        Object proxy = mockMaker.createMock(settings, new MockHandlerImpl(settings));

        assertThat(proxy.getClass()).isEqualTo(typeWithParameters);
        assertThat(
                        new TypeDescription.ForLoadedType(typeWithParameters)
                                .getDeclaredMethods()
                                .filter(named("foo"))
                                .getOnly()
                                .getParameters()
                                .getOnly()
                                .getName())
                .isEqualTo("bar");
    }

    @Test
    public void test_constant_dynamic_compatibility() throws Exception {
        Class<?> typeWithCondy =
                new ByteBuddy()
                        .subclass(Callable.class)
                        .method(named("call"))
                        .intercept(FixedValue.value(JavaConstant.Dynamic.ofNullConstant()))
                        .make()
                        .load(null)
                        .getLoaded();

        MockCreationSettings<?> settings = settingsFor(typeWithCondy);
        @SuppressWarnings("unchecked")
        Object proxy = mockMaker.createMock(settings, new MockHandlerImpl(settings));

        assertThat(proxy.getClass()).isEqualTo(typeWithCondy);
    }

    @Test
    public void test_clear_mock_clears_handler() {
        MockCreationSettings<GenericSubClass> settings = settingsFor(GenericSubClass.class);
        GenericSubClass proxy =
                mockMaker.createMock(settings, new MockHandlerImpl<GenericSubClass>(settings));
        assertThat(mockMaker.getHandler(proxy)).isNotNull();

        // when
        mockMaker.clearMock(proxy);

        // then
        assertThat(mockMaker.getHandler(proxy)).isEqualTo(DisabledMockHandler.HANDLER);
    }

    @Test
    public void test_clear_all_mock_assigns_disabled_handler() {
        MockCreationSettings<GenericSubClass> settings = settingsFor(GenericSubClass.class);
        GenericSubClass proxy1 =
                mockMaker.createMock(settings, new MockHandlerImpl<GenericSubClass>(settings));
        assertThat(mockMaker.getHandler(proxy1)).isNotNull();

        settings = settingsFor(GenericSubClass.class);
        GenericSubClass proxy2 =
                mockMaker.createMock(settings, new MockHandlerImpl<GenericSubClass>(settings));
        assertThat(mockMaker.getHandler(proxy1)).isNotNull();

        // when
        mockMaker.clearAllMocks();

        // then
        assertThat(mockMaker.getHandler(proxy1)).isEqualTo(DisabledMockHandler.HANDLER);
        assertThat(mockMaker.getHandler(proxy2)).isEqualTo(DisabledMockHandler.HANDLER);
    }

    protected static <T> MockCreationSettings<T> settingsFor(
            Class<T> type, Class<?>... extraInterfaces) {
        MockSettingsImpl<T> mockSettings = new MockSettingsImpl<T>();
        mockSettings.setTypeToMock(type);
        mockSettings.defaultAnswer(new Returns("bar"));
        if (extraInterfaces.length > 0) mockSettings.extraInterfaces(extraInterfaces);
        return mockSettings;
    }

    @Test
    public void testMockDispatcherIsRelocated() {
        assertThat(
                        InlineByteBuddyMockMaker.class
                                .getClassLoader()
                                .getResource(
                                        "org/mockito/internal/creation/bytebuddy/inject-MockMethodDispatcher.raw"))
                .isNotNull();

        assertThat(
                        InlineByteBuddyMockMaker.class
                                .getClassLoader()
                                .getResource(
                                        "org/mockito/internal/creation/bytebuddy/inject/MockMethodDispatcher.class"))
                .isNull();
    }

    private static final class FinalClass {

        public String foo() {
            return "foo";
        }
    }

    private static final class FinalSpy {

        private final String aString;
        private final boolean aBoolean;
        private final byte aByte;
        private final short aShort;
        private final char aChar;
        private final int anInt;
        private final long aLong;
        private final float aFloat;
        private final double aDouble;

        private FinalSpy(
                String aString,
                boolean aBoolean,
                byte aByte,
                short aShort,
                char aChar,
                int anInt,
                long aLong,
                float aFloat,
                double aDouble) {
            this.aString = aString;
            this.aBoolean = aBoolean;
            this.aByte = aByte;
            this.aShort = aShort;
            this.aChar = aChar;
            this.anInt = anInt;
            this.aLong = aLong;
            this.aFloat = aFloat;
            this.aDouble = aDouble;
        }
    }

    private static class NonConstructableClass {

        private NonConstructableClass() {
            throw new AssertionError();
        }

        public String foo() {
            return "foo";
        }
    }

    private enum EnumClass {
        INSTANCE;

        public String foo() {
            return "foo";
        }
    }

    private abstract static class FinalMethodAbstractType {

        public final String foo() {
            return "foo";
        }

        public abstract String bar();
    }

    private static class FinalMethod {

        public final String foo() {
            return "foo";
        }
    }

    private interface SampleInterface {

        String bar();
    }

    /*package-private*/ abstract class PackagePrivateSuperClass {

        public abstract String indirect();

        public String value() {
            return indirect() + "qux";
        }
    }

    public class NonPackagePrivateSubClass extends PackagePrivateSuperClass {

        @Override
        public String indirect() {
            return "foo";
        }
    }

    public static class GenericClass<T> {

        public T value() {
            return null;
        }
    }

    public static class WrapperClass {
        public void callWrapped(ExceptionThrowingClass exceptionThrowingClass) throws IOException {
            exceptionThrowingClass.throwException();
        }
    }

    public static class GenericSubClass extends GenericClass<String> {}

    public static class ExceptionThrowingClass {
        public IOException getException() {
            try {
                throwException();
            } catch (IOException ex) {
                return ex;
            }
            return null;
        }

        public void throwException() throws IOException {
            internalThrowException(1);
        }

        void internalThrowException(int test) throws IOException {
            // some lines of code, so the exception is not thrown in the first line of the method
            int i = 0;
            if (test != i) {
                throw new IOException("fatal");
            }
        }
    }

    static class Outer {

        final Object p1;

        private Outer(Object p1) {
            this.p1 = p1;
        }

        private static class Inner extends Outer {

            final Object p2;

            Inner(Object p1, Object p2) {
                super(p1);
                this.p2 = p2;
            }
        }
    }

    public static class SamePackage {

        public final Object p1;

        protected SamePackage(Object p1) {
            this.p1 = p1;
        }
    }
}
