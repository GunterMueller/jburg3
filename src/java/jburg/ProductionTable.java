package jburg;

import java.lang.reflect.*;
import java.util.*;

public class ProductionTable<Nonterminal, NodeType>
{
    private List<Production<Nonterminal, NodeType>>             allProductions  = new ArrayList<Production<Nonterminal, NodeType>>();;
    private List<Closure<Nonterminal>>                          closures        = new ArrayList<Closure<Nonterminal>>();
    private Set<Nonterminal>                                    nonterminals    = new TreeSet<Nonterminal>();
    private Set<NodeType>                                       nodeTypes       = new TreeSet<NodeType>();
    private Set<State<Nonterminal, NodeType>>                   states          = new HashSet<State<Nonterminal, NodeType>>();
    private Map<NodeType, List<Operator<Nonterminal,NodeType>>> operators       = new TreeMap<NodeType, List<Operator<Nonterminal,NodeType>>>();

    private Map<
        NodeType,
        List<Production<Nonterminal, NodeType>>
    > productionsByNodeType = new TreeMap<NodeType, List<Production<Nonterminal, NodeType>>>();

    public Production addPatternMatch(Nonterminal nt, NodeType nodeType, Method method, Nonterminal... childTypes)
    {
        Production<Nonterminal,NodeType> result = new Production<Nonterminal,NodeType>(nt, nodeType, method, childTypes);
        nonterminals.add(nt);
        addProduction(result);
        return result;
    }

    public Closure addClosure(Nonterminal targetNt, Nonterminal sourceNt, Method method)
    {
        Closure<Nonterminal> closure = new Closure<Nonterminal>(targetNt, sourceNt, method);
        closures.add(closure);
        return closure;
    }

    public void addClosure(Nonterminal targetNt, Nonterminal sourceNt)
    {
        addClosure(targetNt, sourceNt, null);
    }

    public void generateStates()
    {
        Stack<State<Nonterminal, NodeType>> worklist = generateLeafStates();

        while (!worklist.empty()) {
            
            State<Nonterminal,NodeType> state = worklist.pop();

            for (List<Operator<Nonterminal,NodeType>> opList: operators.values()) {
                // TODO: This should look at the state's arity and the operator's
                // arity and decide if they're a match.
                for (int i = 1; i < opList.size(); i++) {
                    Operator<Nonterminal,NodeType> op = opList.get(i);
                    if (op != null) {
                        computeTransitions(op, state, worklist);
                    }
                }
            }
        }
    }

    private void closure(State<Nonterminal,NodeType> state)
    {
        boolean closureRecorded;

        do {
            closureRecorded = false;

            for (Closure<Nonterminal> closure: this.closures) {

                // TODO: Use policy to decide whether to let closures displace pattern rules?
                // Can't allow cycles to form.
                if (state.getCost(closure.target) == Integer.MAX_VALUE) {
                    long closureCost = state.getCost(closure.source) + closure.ownCost;

                    if (closureCost < Integer.MAX_VALUE) {
                        System.out.printf("attempting to add closure %s=%s\n",closure.target, closure.source);
                        if(state.addClosure(closure)) {
                            System.out.printf("... added closure %s=%s\n",closure.target, closure.source);
                            closureRecorded = true;
                        }
                    }
                }
            }

        } while (closureRecorded);

        state.finish();
    }

    private List<Production<Nonterminal,NodeType>> getProductions(NodeType op)
    {
        if (!productionsByNodeType.containsKey(op)) {
            productionsByNodeType.put(op, new ArrayList<Production<Nonterminal,NodeType>>());
        }

        return productionsByNodeType.get(op);
    }

    private final List<RepresenterState<Nonterminal, NodeType>> noChildStates = new ArrayList<RepresenterState<Nonterminal, NodeType>>();

    private Stack<State<Nonterminal, NodeType>> generateLeafStates()
    {
        Stack<State<Nonterminal, NodeType>> result = new Stack<State<Nonterminal, NodeType>>();

        for (NodeType nodeType: operators.keySet()) {
            State<Nonterminal, NodeType> state = new State<Nonterminal, NodeType>(nodeType);

            for (Production<Nonterminal, NodeType> p: allProductions) {
                if (p.nodeType == nodeType && p.isLeaf() && p.ownCost < state.getCost(p.target)) {
                    state.setProduction(p, p.ownCost);
                }
            }

            if (state.size() > 0) {
                closure(state);
                result.push(addState(state));
                operators.get(nodeType).get(0).addTransition(noChildStates, state);
            }

        }

        return result;
    }

    private void computeTransitions(Operator<Nonterminal,NodeType> op, State<Nonterminal,NodeType> state, List<State<Nonterminal, NodeType>> workList)
    {
        for (int i = 0; i < op.getArity(); i++) {

            RepresenterState<Nonterminal,NodeType> pState = project(op, i, state);

            if (!op.reps.get(i).contains(pState)) {
                System.out.printf("Added pState {%s} to {%s}\n", pState, op);
                op.reps.get(i).add(pState);

                // Try all permutations of the operator's nonterminal children
                // as operands to the rules applicable to the operator.
                
                List<RepresenterState<Nonterminal,NodeType>> prefix = new ArrayList<RepresenterState<Nonterminal,NodeType>>();
                permute(op, 0, i, pState, prefix, workList);
            }
        }
    }

    private RepresenterState<Nonterminal,NodeType> project(Operator<Nonterminal,NodeType> op, int i, State<Nonterminal,NodeType> state)
    {
        RepresenterState<Nonterminal,NodeType> result = new RepresenterState<Nonterminal,NodeType>(op.nodeType);

        for (Nonterminal n: nonterminals) {
            for (Production<Nonterminal, NodeType> p: getProductions(op.nodeType)) {
                if (p.usesNonterminalAt(n, i) && state.getCost(n) < result.getCost(n)) {
                    result.setCost(n, state.getCost(n));
                }
            }
        }

        return result;
    }

    /**
     * Permute the operator's set of representer states
     * around a possibly novel, 'pivot' representer state,
     * and add any new states discovered to the worklist.
     * @param op        the current operator.
     * @param dim       the next dimension
     * @param pDim      the pivot dimension.
     * @param pivot     the projected state being pivoted.
     * @param prefix    known states so far.
     * @param workList  the worklist for new states.
     */
    private void permute(
        Operator<Nonterminal,NodeType> op,
        int dim,
        int pDim,
        RepresenterState<Nonterminal,NodeType> pivot,
        List<RepresenterState<Nonterminal, NodeType>> prefix,
        List<State<Nonterminal, NodeType>> workList)
    {
        // TODO: Also analyze variadic productions.
        if (dim == op.getArity()) {
            System.out.printf("analyzing rules for %s arity %d against %s\n", op, dim, prefix);

            State<Nonterminal,NodeType> result = new State<Nonterminal,NodeType>(op.nodeType);

            for (Production<Nonterminal, NodeType> p: getProductions(op.nodeType)) {
                // for each state in the prefix:
                //   for each nonterminal in the state:
                //      cost = p.cost + sum of costs in prefix for the current nonterminals;
                //      if (cost < result.getCost(n))
                //          result.addRule(n, cost, p);
                // if result isn't empty:
                //    closure(result);
                //    if (result not in states)
                //        result = addState(result);
                //        worklist.add(result);
                assert(p.size() == dim);
                long cost = 0;
                for (int i = 0; i < dim && cost < Integer.MAX_VALUE; i++) {
                    cost += prefix.get(i).getCost(p.getNonterminal(i));
                }


                if (cost < result.getCost(p.target)) {
                    result.setProduction(p,cost);
                }
            }

            if (!result.empty()) {
                closure(result);
                if (!states.contains(result)) {
                    result = addState(result);
                    workList.add(result);
                }
            }

            op.addTransition(prefix, result);
        }
        else if (dim == pDim) {
            prefix.add(pivot);
            System.out.printf("added pivot %s at %d\n", pivot, dim);
            permute(op,dim+1,pDim,pivot,prefix,workList);
            prefix.remove(dim);
        } else {
            for (RepresenterState<Nonterminal,NodeType> s: op.reps.get(dim)) {
                prefix.add(s);
                System.out.printf("added rep %s at %d\n", pivot, dim);
                permute(op, dim+1, pDim, pivot, prefix, workList);
                prefix.remove(dim);
            }
        }
    }

    private void addProduction(Production<Nonterminal,NodeType> production)
    {
        NodeType nodeType = production.nodeType;

        allProductions.add(production);
        getProductions(nodeType).add(production);
        
        if (!operators.containsKey(nodeType)) {
            operators.put(nodeType, new ArrayList<Operator<Nonterminal,NodeType>>());
        }

        List<Operator<Nonterminal,NodeType>> ops = operators.get(nodeType);

        if (ops.size() < production.size() + 1) {
            ops.addAll(Collections.nCopies((production.size() + 1) - ops.size(), (Operator<Nonterminal,NodeType>)null));
        }

        ops.set(production.size(), new Operator<Nonterminal,NodeType>(nodeType, production.size()));
    }

    private State<Nonterminal, NodeType> addState(State<Nonterminal, NodeType> state)
    {
        if (this.states.add(state)) {
            state.number = this.states.size();
            return state;
        } else {
            Iterator<State<Nonterminal,NodeType>> it = this.states.iterator();
            while (it.hasNext()) {
                State<Nonterminal,NodeType> s = it.next();
                if (state.hashCode() == s.hashCode() && state.equals(s)) {
                    return s;
                }
            }
        }
        throw new IllegalStateException(String.format("State %s not added and not present",state));
    }


    public void dump(java.io.PrintWriter out)
    throws java.io.IOException
    {
        out.println("Operators:");
        for (List<Operator<Nonterminal,NodeType>> opList: operators.values()) {

            for (Operator<Nonterminal,NodeType> op: opList) {
                out.println(op);
            }
        }

        out.println("States:");
        for (State<Nonterminal, NodeType> s: states) {
            out.println(s);
        }

        out.flush();
    }
}
