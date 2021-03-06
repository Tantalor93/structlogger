/**
 * Copyright © 2018, Ondrej Benkovsky
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies,
 * either expressed or implied, of the FreeBSD Project.
 */
package com.github.structlogging.processor;

import com.github.structlogging.StructLogger;
import com.github.structlogging.processor.exception.PackageNameException;
import com.github.structlogging.processor.service.POJOService;
import com.github.structlogging.processor.utils.GeneratedClassInfo;
import com.github.structlogging.processor.utils.MethodAndParameter;
import com.github.structlogging.processor.utils.ScannerParams;
import com.github.structlogging.processor.utils.StatementInfo;
import com.github.structlogging.processor.utils.StructLoggerFieldContext;
import com.github.structlogging.processor.utils.Variable;
import com.github.structlogging.processor.utils.VariableAndValue;
import com.github.structlogging.processor.utils.VariableContextProvider;
import com.github.structlogging.utils.MessageFormatterUtils;
import com.github.structlogging.utils.SidCounter;
import com.squareup.javapoet.JavaFile;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Names;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Name;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * TreePathScanner which takes care of structured log statements replacement
 */
public class LogInvocationScanner extends TreePathScanner<Object, ScannerParams> {


    private final TreeMaker treeMaker;
    private final JavacElements elementUtils;
    private final Names names;
    private final POJOService pojoService;
    private final Messager messager;

    public LogInvocationScanner(final ProcessingEnvironment processingEnvironment) throws IOException, PackageNameException {
        final Context context = ((JavacProcessingEnvironment) processingEnvironment).getContext();

        this.treeMaker = TreeMaker.instance(context);
        this.elementUtils = (JavacElements) processingEnvironment.getElementUtils();
        this.messager = processingEnvironment.getMessager();

        final String generatedEventsPackage = processingEnvironment.getOptions().get("generatedEventsPackage");
        this.pojoService = new POJOService(processingEnvironment.getFiler(), generatedEventsPackage);
        this.names = Names.instance(context);
    }

    /**
     *  Checks expressions, if expression is method call on {@link StructLogger} field, it is considered structured log statement and is
     *  expression is transformed in such way, that method chain is replaced with one call to corresponding infoEvent, errorEvent,... method
     *  with instance of generated class of Event based on method chain
     */
    @Override
    public Object visitExpressionStatement(final ExpressionStatementTree node, final ScannerParams scannerParams) {

        final JCTree.JCExpressionStatement statement = (JCTree.JCExpressionStatement) getCurrentPath().getLeaf();

        final StatementInfo statementInfo = new StatementInfo(
                scannerParams.getCompilationUnitTree().getLineMap().getLineNumber(statement.pos),
                scannerParams.getTypeElement().getQualifiedName().toString(),
                statement
        );

        //inner scanner to go through statement methods
        final TreePathScanner scanner = new TreePathScanner<Object, ScannerParams>() {
            Stack<MethodAndParameter> stack = new Stack<>();

            @Override
            public Object visitMethodInvocation(final MethodInvocationTree node, final ScannerParams o) {
                if (node.getMethodSelect() instanceof JCTree.JCFieldAccess) { // if is call on field
                    try {
                        final JCTree.JCFieldAccess methodSelect = (JCTree.JCFieldAccess) node.getMethodSelect();
                        ExpressionTree parameter = null;
                        if (!node.getArguments().isEmpty()) {
                            parameter = node.getArguments().get(0);
                        }
                        // each method invocation on field is added to stack
                        stack.add(new MethodAndParameter(methodSelect.name, parameter));
                        handle(methodSelect, stack, node, statementInfo, scannerParams);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                return super.visitMethodInvocation(node, o);
            }

        };

        scanner.scan(getCurrentPath(), scannerParams);

        return super.visitExpressionStatement(node, scannerParams);
    }


    /**
     * checks whether fieldAccess node's expression is MEMBER_SELECT or IDENTIFIER
     * and if so then checks that accessed field name corresponds with some declared structlogger field
     * and if it does correspond, then whole statement is handled as structlogger expression
     * @param fieldAccess to be analyzed
     * @param stack filled with previous method calls
     * @param node AST node
     * @param statementInfo info about analyzed statement
     * @param scannerParams params passed from processor
     */
    private void handle(final JCTree.JCFieldAccess fieldAccess,
                        final Stack<MethodAndParameter> stack,
                        final MethodInvocationTree node,
                        final StatementInfo statementInfo,
                        final ScannerParams scannerParams) {
        if (fieldAccess.getExpression().getKind().equals( Tree.Kind.MEMBER_SELECT)) {
            //to handle when structlogger field is referenced through this.field and ClassName.field
            final MemberSelectTree expression = (MemberSelectTree) fieldAccess.getExpression();
            final Name name = expression.getIdentifier();
            if (scannerParams.getFields().containsKey(name)) {
                handleStructLogExpression(stack, node, name, statementInfo, scannerParams);
            }
        }
        else if (fieldAccess.getExpression().getKind().equals( Tree.Kind.IDENTIFIER)) {
            // to handle when structlogger field is referenced directly
            final JCTree.JCIdent ident = (JCTree.JCIdent) fieldAccess.getExpression();
            final Name name = ident.getName();
            if (scannerParams.getFields().containsKey(name)) {
                handleStructLogExpression(stack, node, name, statementInfo, scannerParams);
            }
        }
    }

    /**
     * structlogger statement which uses fluent API is replaced with form, which uses
     * generated logging event, this method does all the checks of statement
     *
     * @param stack all method calls of structlogger field on one line
     * @param node to analyze
     * @param name of field
     * @param statementInfo about whole one line statement
     */
    private void handleStructLogExpression(final Stack<MethodAndParameter> stack,
                                           final MethodInvocationTree node,
                                           final Name name,
                                           final StatementInfo statementInfo,
                                           final ScannerParams scannerParams) {
        final java.util.List<VariableAndValue> usedVariables = new ArrayList<>();
        JCTree.JCLiteral literal = null;
        String level = null;
        String eventName = null;

        //statement check
        final StructLoggerFieldContext structLoggerFieldContext = scannerParams.getFields().get(name);
        final TypeMirror typeMirror = structLoggerFieldContext.getContextProvider();
        final VariableContextProvider variableContextProvider = scannerParams.getVarsHashMap().get(typeMirror);

        //go through each call of method in this method and check whether it can be mapped to logging variable provided by
        //VarContextProvider or it is logLevelMethod or log method call
        while (!stack.empty()) {
            // check whether method on stack matched variable from context provider, or is log or log level method
            boolean matched = false;
            final MethodAndParameter top = stack.pop();
            //go through each variable and check whether it matches
            for (Variable variable : variableContextProvider.getVariables()) {
                final Name topMethodName = top.getMethodName();

                if (variable.getName().equals(topMethodName)) {
                    addToUsedVariables(usedVariables, top, variable);
                    matched = true;
                    break;
                }
                else {
                    for (LogLevel logLevel : LogLevel.values()) {
                        if (topMethodName.contentEquals(logLevel.getLevelMethodName())) {
                            if (!(node.getArguments().get(0) instanceof JCTree.JCLiteral)) {
                                printStatementMustHaveStringLiteralError(statementInfo, topMethodName);
                                return;
                            }
                            literal = (JCTree.JCLiteral) node.getArguments().get(0);
                            level = logLevel.getLevelName();
                            matched = true;
                            break;
                        }
                        else if (topMethodName.contentEquals(logLevel.getLogEventMethodName())) {
                            return; // nothing to do here, no code replacement needed
                        }
                    }
                }
            }

            if (top.getMethodName().contentEquals("log") && top.getParameter() != null) {
                if (!(top.getParameter() instanceof JCTree.JCLiteral)) {
                    printStatementMustHaveStringLiteralError(statementInfo, top.getMethodName());
                    return;
                }
                eventName = ((JCTree.JCLiteral) top.getParameter()).getValue().toString();
                if (!Pattern.compile("^(\\w+(\\.\\w+)*)+$").matcher(eventName).matches()) {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            formatWithStatementLocation(
                                    "qualified event name %s specified by %s statement is not valid",
                                    statementInfo,
                                    eventName,
                                    statementInfo.getStatement()
                            )
                    );
                    return;
                }
                matched = true;
            } else if (top.getMethodName().contentEquals("log") && top.getParameter() == null) {
                matched = true;
            }
            if (stack.empty() && !top.getMethodName().contentEquals("log")) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        formatWithStatementLocation(
                                "statement %s must be ended by calling log() method",
                                statementInfo,
                                statementInfo.getStatement()
                        )
                );
                return;
            }
            if (!matched) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        formatWithStatementLocation(
                                "variable %s in statement %s is not specified by variable context %s",
                                statementInfo,
                                top.getMethodName(),
                                statementInfo.getStatement(),
                                variableContextProvider.getTypeMirror()
                        )
                );
                return;
            }
        }

        //parametrization check
        if (variableContextProvider.shouldParametrize()) {
            final int countOfStringVariables = StringUtils.countMatches(literal.getValue().toString(), "{}");
            if (countOfStringVariables != usedVariables.size()) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        formatWithStatementLocation(
                                "literal %s contains %d variables, but statement %s uses %d variables",
                                statementInfo,
                                literal.getValue().toString(),
                                countOfStringVariables,
                                statementInfo.getStatement(),
                                usedVariables.size()
                        )
                );
                return;
            }
        }

        //event class generation
        JavaFile javaFile;
        try {
            javaFile = pojoService.createPojo(eventName, literal, usedVariables);
        } catch (PackageNameException e) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    formatWithStatementLocation(
                            "qualified event name %s generated by statement %s is not valid, please check specified event name and package does not contain java keyword or no subpackage or class name starts with number",
                            statementInfo,
                            eventName,
                            statementInfo.getStatement()
                    )
            );
            return;
        }
        final String className = javaFile.typeSpec.name;
        final String qualifiedName = StringUtils.isBlank(javaFile.packageName) ? className : javaFile.packageName + "." + className;
        final GeneratedClassInfo generatedClassInfo = new GeneratedClassInfo(qualifiedName, className, (String) literal.getValue(), usedVariables, javaFile.packageName);
        for (GeneratedClassInfo info : scannerParams.getGeneratedClassesInfo()) {
            if (info.getQualifiedName().equals(generatedClassInfo.getQualifiedName())
                    && !info.getUsedVariables().equals(generatedClassInfo.getUsedVariables())
                    ) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        formatWithStatementLocation(
                                "Statement %s generates different event structure for same event name",
                                statementInfo,
                                statementInfo.getStatement()
                        )
                );
                return;
            }
        }
        scannerParams.getGeneratedClassesInfo().add(generatedClassInfo);

        pojoService.writeJavaFile(javaFile);

        //replace statement
        replaceInCode(name.toString(), generatedClassInfo, statementInfo, usedVariables, literal, level, variableContextProvider);
    }

    private void printStatementMustHaveStringLiteralError(final StatementInfo statementInfo, final Name topMethodName) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                formatWithStatementLocation(
                        "method %s in %s statement must have String literal as argument",
                        statementInfo,
                        topMethodName,
                        statementInfo.getStatement()
                )
        );
    }

    //System.format with string representing statement location added at the end
    private String formatWithStatementLocation(String format, StatementInfo statementInfo, Object... args) {
        return format(format, args) + format(" [%s:%s]", statementInfo.getSourceFileName(), statementInfo.getLineNumber());
    }

    //adds variable to usedVariables, if variable is already present in used variables, variable is added to used variables
    //with incremented name, e.g. if used variables are A,B and we want to add A, it is added as A1
    private void addToUsedVariables(final java.util.List<VariableAndValue> usedVariables, final MethodAndParameter top, final Variable variable) {
        VariableAndValue variableAndValue = new VariableAndValue(variable, top.getParameter());
        if (!usedVariables.contains(variableAndValue)) {
            usedVariables.add(variableAndValue);
        } else {
            int i = 0;
            do {
                i++;
                variableAndValue = new VariableAndValue(
                        new Variable(
                                elementUtils.getName(variable.getName().toString() + i),
                                variable.getType()
                        ),
                        top.getParameter()
                );
            } while (usedVariables.contains(variableAndValue));
            usedVariables.add(variableAndValue);
        }
    }

    /**
     * replaces statement with our improved call to {@link StructLogger}
     */
    private void replaceInCode(final String loggerName, final GeneratedClassInfo generatedClassInfo, final StatementInfo statementInfo, java.util.List<VariableAndValue> usedVariables, JCTree.JCLiteral literal, String level, VariableContextProvider variableContextProvider) {
        final ListBuffer listBuffer = new ListBuffer();
        final Class<SidCounter> sidCounterClass = SidCounter.class;

        if(variableContextProvider.shouldParametrize()) {
            listBuffer.add(createFormatCall(usedVariables, literal));
        }
        else {
            listBuffer.add(literal);
        }

        listBuffer.add(treeMaker.Literal(statementInfo.getSourceFileName()));
        listBuffer.add(treeMaker.Literal(statementInfo.getLineNumber()));
        listBuffer.add(treeMaker.Literal(generatedClassInfo.getQualifiedName()));
        listBuffer.add(treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(
                        treeMaker.Select(
                                treeMaker.Ident(
                                        names.fromString(
                                                sidCounterClass.getPackage().getName()
                                        )
                                ),
                                names.fromString(
                                        sidCounterClass.getSimpleName()
                                )
                        ),
                        names.fromString("incrementAndGet")
                ),
                List.nil())
        );
        listBuffer.add(treeMaker.Literal(level));
        listBuffer.add(treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(
                        treeMaker.Select(
                                treeMaker.Ident(
                                        names.fromString(
                                                System.class.getPackage().getName()
                                        )
                                ),
                                names.fromString(
                                        System.class.getSimpleName()
                                )
                        ),
                        names.fromString("currentTimeMillis")
                ),
                List.nil())
        );
        addVariablesToBuffer(usedVariables, listBuffer);

        final JCTree.JCNewClass jcNewClass = treeMaker.NewClass(
                null,
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(
                        treeMaker.Ident(
                                names.fromString(generatedClassInfo.getPackageName())
                        ),
                        names.fromString(generatedClassInfo.getSimpleName())
                ),
                listBuffer.toList(),
                null);

        final JCTree.JCMethodInvocation apply = treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(
                        treeMaker.Ident(
                                elementUtils.getName(loggerName)
                        ),
                        elementUtils.getName(level.toLowerCase() + "Event")
                ),
                com.sun.tools.javac.util.List.of(
                        jcNewClass
                )
        );
        statementInfo.getStatement().expr = apply;
    }

    /**
     *
     * @param usedVariables used variables list
     * @param literal string literal to be used as format pattern
     * @return proper call to MessageFormatterUtils.format with arguments
     */
    private JCTree.JCMethodInvocation createFormatCall(final java.util.List<VariableAndValue> usedVariables, final JCTree.JCLiteral literal) {
        final ListBuffer lb = new ListBuffer();
        lb.add(literal);
        addVariablesToBuffer(usedVariables, lb);

        final Class<MessageFormatterUtils> messageFormatterUtilsClass = MessageFormatterUtils.class;

        return treeMaker.Apply(
                List.nil(),
                treeMaker.Select(
                        treeMaker.Select(
                                treeMaker.Ident(
                                        names.fromString(
                                                messageFormatterUtilsClass.getPackage().getName()
                                        )
                                ),
                                names.fromString(messageFormatterUtilsClass.getSimpleName())
                        ),
                        names.fromString("format")
                ),
                lb.toList()
        );
    }

    // all used variables are added to listbuffer
    private void addVariablesToBuffer(final java.util.List<VariableAndValue> usedVariables, final ListBuffer listBuffer) {
        for (VariableAndValue variableAndValue : usedVariables) {
            listBuffer.add(variableAndValue.getValue());
        }
    }

}
