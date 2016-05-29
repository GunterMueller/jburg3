package jburg;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * A PatternMatcher encodes a opcode(operand, operand) pattern match.
 * Note that PatternMatcher uses identity semantics for its hashCode()
 * and equals() methods (i.e., it doesn't override Object), which
 * is used by the State objects that contain aggregations of
 * PatternMatcher and Closure productions, which can create an unbounded
 * number of candidate States that only differ in that their costs
 * are increasing due to closures; these states don't contribute any
 * novel information and so the State defines its hashCode and equals
 * methods in terms of the PatternMatcher map's hashCode() and equals().
 */
public class PatternMatcher<Nonterminal, NodeType> extends Production<Nonterminal>
{
    /**
     * The node type of the subtree root.
     */
    public final NodeType              nodeType;

    /**
     * The nonterminal types the root's children must produce.
     */
    public final List<Nonterminal>     childTypes;

    public PatternMatcher(Nonterminal target, NodeType nodeType, int cost, Method predicate, Method preCallback, Method postCallback, boolean isVarArgs, List<Nonterminal> childTypes)
    {
        super(target, cost, isVarArgs, predicate, preCallback, postCallback);

        this.nodeType       = nodeType;
        this.childTypes     = childTypes;
    }

    public Nonterminal getNonterminal(int index)
    {
        if (isVarArgs && index >= size()) {
            return childTypes.get(childTypes.size() - 1);
        } else {
            return childTypes.get(index);
        }
    }

    public boolean usesNonterminalAt(Nonterminal n, int index)
    {
        if (isVarArgs && index >= size()) {
            return getNonterminal(size()-1) == n;
        } else {
            return index < childTypes.size() && getNonterminal(index) == n;
        }
    }

    public boolean isLeaf()
    {
        return childTypes.isEmpty();
    }

    public int size()
    {
        return childTypes.size();
    }

    public boolean acceptsDimension(int dim)
    {
        return isVarArgs? size() <= dim: size() == dim;
    }

    @Override
    public String toString()
    {
        return String.format("%s%s:%s)", nodeType, childTypes, ownCost);
    }
}
