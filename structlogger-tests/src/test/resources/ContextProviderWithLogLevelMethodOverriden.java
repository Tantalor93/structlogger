import cz.muni.fi.annotation.Var;
import cz.muni.fi.annotation.VarContextProvider;
import cz.muni.fi.VariableContext;

@VarContextProvider
public interface ContextProviderWithLogLevelMethodOverriden extends VariableContext {

    @Var
    ContextProviderBadMethodNames info(long var);
}
