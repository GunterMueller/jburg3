package jburg;

import java.util.*;

/**
 * A Reducer is the actual tree parsing automaton.
 * The reducer works in two passes:
 * <li> label(node) traverses the input tree and
 * assigns each node a state number.
 * <li> reduce(node) traverses the tree a second
 * time, running the productions specified by each
 * node's state number to rewrite the tree as
 * specified by the productions.
 */
public class Reducer<Nonterminal, NodeType>
{
    final Object visitor;
    final ProductionTable<Nonterminal, NodeType> productionTable;

    public Reducer(Object visitor, ProductionTable<Nonterminal, NodeType> productionTable)
    {
        this.visitor = visitor;
        this.productionTable = productionTable;
    }

    /**
     * First pass: label a tree.
     * @param node the root of the tree to label.
     */
    public void label(BurgInput<NodeType> node)
    {
        Operator<Nonterminal, NodeType> op = productionTable.getOperator(node.getNodeType(), node.getSubtreeCount());

        if (op != null) {
            int subtreeCount = node.getSubtreeCount();

            if (subtreeCount > 0) {

                for (int i = 0; i < subtreeCount; i++) {
                    label(node.getSubtree(i));
                }

                // Navigate the operator's transition table.
                HyperPlane<Nonterminal, NodeType> current = op.transitionTable;

                for (int dim = 0; dim < node.getSubtreeCount(); dim++) {
                    RepresenterState<Nonterminal, NodeType> rs = op.getRepresenterState(node.getSubtree(dim).getStateNumber(), dim);

                    if (dim < subtreeCount-1) {
                        current = current.getNextDimension(rs);
                    } else {
                        node.setStateNumber(current.getResultState(rs).number);
                    }
                }

            } else {
                node.setStateNumber(op.getLeafState().number);
            }
        }
    }

    /**
     * Second pass: reduce the tree to the desired goal.
     * @param node the root of the tree.
     * @param goal the nonterminal corresponding to the
     * desired result object.
     * @return the result of deriving the tree using the
     * productions specified by each node's state.
     */
    public Object reduce(BurgInput<NodeType> node, Nonterminal goal)
    throws Exception
    {
        // TODO: Run the entire algorithm iteratively, using the stack.
        Stack<Production<Nonterminal>> productions = new Stack<Production<Nonterminal>>();
        return reduce(node, goal, productions);
    }

    /**
     * Recursively reduce subtrees.
     * @param node the root of a subtree.
     * @param goal the subtree's state.
     * @param pendingProductions    a stack of productions whose
     * antecedent production has yet to be completed; passed here
     * to avoid extra object creation, and eventually to be used
     * to run the algorithm iteratively.
     * @return the result of deriving the subtree.
     * @throws exceptions from the production's semantic action routines,
     * and a few diagnostics for unlabeled trees or mismatched parameters.
     */
    private Object reduce(BurgInput<NodeType> node, Nonterminal goal, Stack<Production<Nonterminal>> pendingProductions)
    throws Exception
    {
        if (node.getStateNumber() < 0) {
            throw new IllegalStateException(String.format("Unlabeled node %s",node));
        }

        State<Nonterminal,NodeType> state = productionTable.getState(node.getStateNumber());

        // Run pre-callbacks on any closures to get to the pattern matcher.
        Production<Nonterminal> current = state.getProduction(goal);

        while(current instanceof Closure) {
            if (current.preCallback != null) {
                current.preCallback.invoke(visitor, node, goal);
            }
            pendingProductions.push(current);
            current = state.getProduction(((Closure<Nonterminal>)current).source);
        }

        if (current.preCallback != null) {
            current.preCallback.invoke(visitor, node,goal);
        }

        Object result = null;

        // Reduce children and collect results
        if (current.postCallback != null) {

            assert current instanceof PatternMatcher;
            @SuppressWarnings("unchecked")
			PatternMatcher<Nonterminal, NodeType> patternMatcher = (PatternMatcher<Nonterminal, NodeType>)current;

            switch(node.getSubtreeCount()) {
                case 0:
                    result = current.postCallback.invoke(visitor, node);
                    break;

                case 1:
                    result = current.postCallback.invoke(visitor, node, reduce(node.getSubtree(0), patternMatcher.getNonterminal(0)));
                    break;

                case 2:
                    result = current.postCallback.invoke(
                        visitor,
                        node,
                        reduce(node.getSubtree(0), patternMatcher.getNonterminal(0)),
                        reduce(node.getSubtree(1), patternMatcher.getNonterminal(1))
                        );
                    break;

                default: {

                    int formalCount = current.postCallback.getParameterCount();
                    // The actual parameters are the root of the subtree itself,
                    // plus the result of reducing each of the root's children.
                    int actualCount = node.getSubtreeCount() + 1;

                    Object[] actuals = new Object[formalCount];
                    actuals[0] = node;

                    if (formalCount == actualCount) {
                        for (int i = 0; i < node.getSubtreeCount(); i++) {
                            actuals[i+1] = reduce(node.getSubtree(i), patternMatcher.getNonterminal(i));
                        }

                    } else if (formalCount < actualCount) {
                        throw new IllegalStateException("variadic callbacks not supported yet.");

                    } else {
                        throw new IllegalStateException(String.format("Method %s expected %d actuals, received %d", current.postCallback, formalCount, actualCount));
                    }

                    result = current.postCallback.invoke(visitor, actuals);
                }
            }
        }

        while (!pendingProductions.isEmpty()) {
            Production<Nonterminal> closure = pendingProductions.pop();
            if (closure.postCallback != null) {
                result = closure.postCallback.invoke(visitor, node, result);
            }
        }

        return result;
    }
}
