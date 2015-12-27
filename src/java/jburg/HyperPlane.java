package jburg;

import java.util.*;

/**
 * A HyperPlane is a representation of one dimension
 * of a multi-dimensional map of child state tuples
 * to the corresponding states of the subtree root.
 * Each Operator has a reference to the first dimension
 * of its multidimensional map; each dimension maps its
 * representer states to the next dimension, or in the
 * case of the final dimension, to the states which can
 * be assigned to the subtree root.
 * <p>A note on variadic pattern matchers: the final
 * dimension of a variadic matcher contains both mappings
 * (back to itself) for the next dimension and mappings
 * to the resultant states at the last element in the tuple.
 */
class HyperPlane<Nonterminal, NodeType>
{
    /**
     * The next dimension of the map, if this is not the final dimension.
     */
    final Map<RepresenterState<Nonterminal, NodeType>, HyperPlane<Nonterminal, NodeType>> nextDimension;
    /**
     * The states in this dimension, if this is the final dimension.
     */
    final Map<RepresenterState<Nonterminal, NodeType>, State<Nonterminal, NodeType>>      finalDimension;

    HyperPlane()
    {
        nextDimension   = new HashMap<RepresenterState<Nonterminal, NodeType>, HyperPlane<Nonterminal, NodeType>>();
        finalDimension  = new HashMap<RepresenterState<Nonterminal, NodeType>, State<Nonterminal, NodeType>>();
    }

    void add(List<RepresenterState<Nonterminal, NodeType>> childStates, int currentDim, State<Nonterminal, NodeType> resultantState)
    {

        assert childStates.size() > 0;

        RepresenterState<Nonterminal, NodeType> key = childStates.get(currentDim);

        if (currentDim < childStates.size() - 1) {

            if (!nextDimension.containsKey(key)) {
                nextDimension.put(key, new HyperPlane<Nonterminal, NodeType>());
            }
            nextDimension.get(key).add(childStates, currentDim+1, resultantState);
        } else {
            finalDimension.put(key, resultantState);

            // If all the states in this final dimension are variadic,
            // add a variadic transition back to this hyper plane.
            if (isVarArgs()) {
                nextDimension.put(key, this);
            }
        }
    }

    /**
     * Is this HyperPlane variadic?
     * @return true if all this HyperPlane's productions,
     * as well as all its descendents' productions, are variadic.
     */
    boolean isVarArgs()
    {
        for (State<Nonterminal, NodeType> s: finalDimension.values()) {

            if (!s.isVarArgs()) {
                return false;
            }
        }

        for (HyperPlane<Nonterminal, NodeType> child: nextDimension.values()) {
            if (!(child == this || child.isVarArgs())) {
                return false;
            }
        }

        return true;
    }

    HyperPlane<Nonterminal, NodeType> getNextDimension(RepresenterState<Nonterminal, NodeType> rs)
    {
        HyperPlane<Nonterminal, NodeType> result = nextDimension.get(rs);

        if (result == null) {
            throw new IllegalStateException(String.format("No hyperplane mapping for %s", rs));
        }

        return result;
    }

    State<Nonterminal, NodeType> getResultState(RepresenterState<Nonterminal, NodeType> rs)
    {
        State<Nonterminal, NodeType> result = finalDimension.get(rs);

        if (result == null) {
            throw new IllegalStateException(String.format("No hyperplane mapping for %s", rs));
        }

        return result;
    }

    @Override
    public String toString()
    {
        if (nextDimension.isEmpty()) {
            return finalDimension.toString();

        } else {
            return String.format("HyperPlane{%s %s}", nextDimension, finalDimension);
        }
    }

    void dump(java.io.PrintWriter out)
    throws java.io.IOException
    {
        if (nextDimension.isEmpty()) {

            for (RepresenterState<Nonterminal, NodeType> key: finalDimension.keySet()) {

                for (State<Nonterminal, NodeType> s: key.representedStates) {
                    s.miniDump(out);
                }
            }
        } else {
                
            for (RepresenterState<Nonterminal, NodeType> key: nextDimension.keySet()) {

                for (State<Nonterminal, NodeType> s: key.representedStates) {
                    out.printf("<plane state=\"%d\">", s.number);
                    if (nextDimension.get(key) != this) {
                        nextDimension.get(key).dump(out);
                    } else {
                        out.println("<variadic/>");
                    }
                    out.println("</plane>");
                }
            }
        }
    }
}
