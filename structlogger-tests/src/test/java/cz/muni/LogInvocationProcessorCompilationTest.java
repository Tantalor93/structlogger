package cz.muni;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import cz.muni.fi.processor.LogInvocationProcessor;
import org.junit.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class LogInvocationProcessorCompilationTest {

    @Test
    public void shouldNotCompileInsufficientParametrization() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("InsufficientParametrization.java"));

        assertThat(compilation).hadErrorContaining(
                "literal Should not compile {} contains 1 variables, but statement defaultLog.info(\"Should not compile {}\").varDouble(1.2).varBoolean(false).log(); uses 2 variables [InsufficientParametrization:16]"
        );
    }

    @Test
    public void shouldNotCompileLogMethodNotCalled() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("LogMethodNotCalled.java"));

        assertThat(compilation).hadErrorContaining(
                "statement defaultLog.info(\"Should not compile {} {}\").varDouble(1.2).varBoolean(false); must be ended by calling log() method [LogMethodNotCalled:16]"
        );
    }

    @Test
    public void shouldNotCompileInvalidQualifiedEventNameJavaKeyword() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("InvalidQualifiedEventNameJavaKeyword.java"));

        assertThat(compilation).hadErrorContaining(
                "qualified event name some.package.ShouldNotBeCreated generated by statement defaultLog.info(\"Should not compile {} {}\").varDouble(1.2).varBoolean(false).log(\"some.package.ShouldNotBeCreated\"); is not valid, please check specified event name and package does not contain java keyword or no subpackage or class name starts with number [InvalidQualifiedEventNameJavaKeyword:16]"
        );
    }

    @Test
    public void shouldNotCompileInvalidQualifiedEventNameStartWithNumber() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("InvalidQualifiedEventNameStartWithNumber.java"));

        assertThat(compilation).hadErrorContaining(
                "qualified event name some.different.1ShouldNotBeCreated generated by statement defaultLog.info(\"Should not compile {} {}\").varDouble(1.2).varBoolean(false).log(\"some.different.1ShouldNotBeCreated\"); is not valid, please check specified event name and package does not contain java keyword or no subpackage or class name starts with number [InvalidQualifiedEventNameStartWithNumber:16]"
        );
    }

    @Test
    public void shouldNotCompileInvalidQualifiedEventNameInvalidChar() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("InvalidQualifiedEventNameInvalidChar.java"));

        assertThat(compilation).hadErrorContaining(
                "qualified event name some.different.\"ShouldNotBeCreated specified by defaultLog.info(\"Should not compile {} {}\").varDouble(1.2).varBoolean(false).log(\"some.different.\\\"ShouldNotBeCreated\"); statement is not valid [InvalidQualifiedEventNameInvalidChar:16]"
        );
    }

    @Test
    public void shouldNotCompileEventStructureCollision() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("EventStructureCollision.java"));

        assertThat(compilation).hadErrorContaining(
                "Statement defaultLog.info(\"Should not compile {} {}\").varInt(1.2).varBoolean(false).log(\"CollisionEvent\"); generates different event structure for same event name [EventStructureCollision:21]"
        );
    }

    @Test
    public void shouldNotCompileInvalidLogMethodArgument() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("InvalidArgumentLogMethod.java"));

        assertThat(compilation).hadErrorContaining(
                "method log in defaultLog.info(\"Should not compile {} {}\").varDouble(1.2).varBoolean(false).log(value); statement must have String literal as argument [InvalidArgumentLogMethod:18]"
        );
    }

    @Test
    public void shouldNotCompileInvalidLogLevelMethodArgument() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("InvalidArgumentLogLevelMethod.java"));

        assertThat(compilation).hadErrorContaining(
                "method info in defaultLog.info(value).varDouble(1.2).varBoolean(false).log(); statement must have String literal as argument [InvalidArgumentLogLevelMethod:18]"
        );
    }

    @Test
    public void shouldNotCompileContextProviderNotAnnotated() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("ContextProviderNotAnnotated.java"),
                                JavaFileObjects.forResource("UseNotAnnotatedProvider.java"));

        assertThat(compilation).hadErrorContaining(
                "ContextProviderNotAnnotated should be annotated with @VarContextProvider"
        );
    }


    @Test
    public void shouldNotCompileContextProviderNotExtendingVarContext() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("ContextProviderNotExtending.java"),
                                JavaFileObjects.forResource("UseProviderWhichDoesNotExtend.java"));

        assertThat(compilation).hadErrorContaining(
                "ContextProviderNotExtending should be extending cz.muni.fi.VariableContext"
        );
    }

    @Test
    public void shouldNotCompileContextProviderWithLogMethodOverriden() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("ContextProviderWithOverridenLogMethod.java"),
                                JavaFileObjects.forResource("UseProviderWithOverridenLogMethod.java"));

        assertThat(compilation).hadErrorContaining(
                "ContextProviderWithOverridenLogMethod interface cannot have method named log"
        );
    }


    @Test
    public void shouldNotCompileContextProviderWithLogLevelMethodOverriden() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("ContextProviderWithLogLevelMethodOverriden.java"),
                                JavaFileObjects.forResource("UseProviderWithLogLevelMethodOverriden.java"));

        assertThat(compilation).hadErrorContaining(
                "ContextProviderWithLogLevelMethodOverriden interface cannot have method named info"
        );
    }

    @Test
    public void shouldNotCompileContextProviderWithBadReturnType() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("ContextProviderBadReturnType.java"),
                                JavaFileObjects.forResource("UseProviderWithBadReturnType.java"));

        assertThat(compilation).hadErrorContaining(
                "ContextProviderBadReturnType.varLong method must have return type ContextProviderBadReturnType"
        );
    }

    @Test
    public void shouldNotCompileContextProviderWithMultipleArgumentVar() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("ContextProviderMultipleArgumentVar.java"),
                                JavaFileObjects.forResource("UseProviderWithMultipleArgumentVar.java"));

        assertThat(compilation).hadErrorContaining(
                "ContextProviderMultipleArgumentVar.varLong method must have exactly one argument"
        );
    }

    @Test
    public void shouldNotCompileContextProviderNoVar() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("ContextProviderNoVar.java"),
                                JavaFileObjects.forResource("UseProviderWithNoVar.java"));

        assertThat(compilation).hadWarningContaining(
                "ContextProviderNoVar has no @Var annotated methods"
        );
    }

    @Test
    public void shouldNotCompileContextProviderUsingNotAnnotatedVar() {
        final Compilation compilation =
                javac()
                        .withProcessors(new LogInvocationProcessor())
                        .compile(JavaFileObjects.forResource("ContextProviderOneVarMissing.java"),
                                JavaFileObjects.forResource("UseProviderWithOneVarMissing.java"));

        assertThat(compilation).hadErrorContaining(
                "variable varString in statement defaultLog.info(\"Should not compile\").varLong(1L).varString(\"testik\").log(); is not specified by variable context ContextProviderOneVarMissing [UseProviderWithOneVarMissing:16]"
        );
    }
}
