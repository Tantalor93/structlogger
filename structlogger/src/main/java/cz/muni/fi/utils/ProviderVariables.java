package cz.muni.fi.utils;

import cz.muni.fi.annotation.VarContextProvider;

import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Class representing class annotated with {@link VarContextProvider}
 */
public class ProviderVariables {

    private TypeMirror typeMirror;
    private List<Variable> variables;

    public ProviderVariables(final TypeMirror typeMirror, final List<Variable> variables) {
        this.typeMirror = typeMirror;
        this.variables = variables;
    }

    /**
     *
     * @return TypeMirror of {@link VarContextProvider} annotated class
     */
    public TypeMirror getTypeMirror() {
        return typeMirror;
    }

    /**
     *
     * @return List of variables provided by {@link VarContextProvider} annotated class
     */
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public String toString() {
        return "ProviderVariables{" +
                "typeMirror=" + typeMirror +
                ", variables=" + variables +
                '}';
    }
}