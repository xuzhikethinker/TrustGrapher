////////////////////////////////MyReputationGraph///////////////////////////////
package trustGrapher.graph;

import trustGrapher.graph.edges.MyReputationEdge;
import cu.repsystestbed.entities.Agent;
import cu.repsystestbed.graphs.ReputationEdgeFactory;
import cu.repsystestbed.graphs.ReputationGraph;
import cu.repsystestbed.graphs.TestbedEdge;
import java.util.ArrayList;
import java.util.Collection;
import org.jgrapht.graph.SimpleDirectedGraph;
import trustGrapher.algorithms.MyEigenTrust;
import trustGrapher.visualizer.eventplayer.TrustLogEvent;
import utilities.ChatterBox;

/**
 * A  graph that displays each agent's trust towards other agents that it's had transactions with.
 * @author Andrew O'Hara
 */
public class MyReputationGraph extends MyGraph {
    private MyReputationGraph fullGraph = null;
    public MyEigenTrust alg;

//////////////////////////////////Constructor///////////////////////////////////

    /**
     * Creates a full graph.  The components of this graph are actually the ones beign displayed.
     * @param baseGraph The graph that this graph will be based on
     */
    public MyReputationGraph(ReputationGraph baseGraph){
        super((SimpleDirectedGraph)baseGraph, FULL);
    }

    /**
     * Creates a dynamic graph.  This graph is only used by the full graph to see which of it's own components should be displayed.
     * @param baseGraph The graph that this graph will be based on
     * @param fullGraph A reference to the fullGraph so that reputation can be changed
     */
    public MyReputationGraph(MyReputationGraph fullGraph, MyEigenTrust alg) {
        super((SimpleDirectedGraph) new ReputationGraph(new ReputationEdgeFactory()), DYNAMIC);
        this.fullGraph = fullGraph;
        this.alg = alg;
    }

//////////////////////////////////Accessors/////////////////////////////////////
    public MyReputationGraph getFullGraph(){
        if (type != DYNAMIC){
            ChatterBox.error(this, "getFullGraph()", "This is not a dynamic graph.  This method cannot be called.");
        }
        return fullGraph;
    }

///////////////////////////////////Methods//////////////////////////////////////

    /**
     * For use by the TrustEventLoader when the graphs in constructed to clear all reputation.
     */
    public void removeRep(){
        if (type == DYNAMIC){
            ChatterBox.error(this, "removeRep()", "This graph is not a full graph.  Illegal method call.");
            return;
        }
        for (TestbedEdge e: getEdges()){
            ((MyReputationEdge)e).setReputation(0);
        }
    }

    /**
     * Adds any new Agents that are necessary, then looks up the trustscore for every agent in the graph.
     * If an edge doesn't exist, it is  created.  The reputation is actually changed on the matching edge on the full graph
     * @param fullGraph     Any reputation changes will be done to the edges on this graph
     * @param from      The id of the peer that just gave feedback
     * @param to        The id of the peer that just recieved feedback
     */
    public void feedback(MyReputationGraph fullGraph, int from, int to) {
        if (type == FULL){
            ChatterBox.error(this, "feedback()", "This graph is not a dynamic graph.  Illegal method call.");
            return;
        }
        alg.setMatrixFilled(false);
        alg.setIterations(alg.getIterations() + 1);
        this.addPeer(from);
        this.addPeer(to);
        for (Agent src : alg.getFeedbackGraph().vertexSet()){
            for (Agent sink : alg.getFeedbackGraph().vertexSet()){
                if (!src.equals(sink)){
                    double trustScore = alg.calculateTrustScore(src, sink);
                    if (findEdge(src,sink) == null  && trustScore != 0.0){
                        int id = ((MyReputationEdge)fullGraph.findEdge(src, sink)).getID();                     
                        MyReputationEdge edge = createEdge(src, sink, id);
                        addEdge(edge, src, sink);
                    }
                    ((MyReputationEdge)fullGraph.findEdge(src, sink)).setReputation(trustScore);
                }
            }
        }
    }

    /**
     * For use by graphConstructionEvent() only.  Calls the createEdge(Agent, Agent, int) method.
     * Passes on the src and sink, but gives it an incrementing id starting at 0.
     * @param src
     * @param sink
     * @return a MyReputationEdge between the given agents
     */
    public MyReputationEdge createEdge(Agent src, Agent sink){
        if (type == DYNAMIC){
            ChatterBox.error(this, "createEdge()", "This graph is not a full graph.  Illegal method call.");
            return null;
        }
        return createEdge(src, sink, edgecounter++);
    }

    /**
     * Creates a MyReputatioEdge and returns a reference to it
     * @param src
     * @param sink
     * @param id
     * @return a MyReputationEdge between the given agents with the given id
     */
    public MyReputationEdge createEdge(Agent src, Agent sink, int id){
        if (getVertexInGraph(src.id) == null) {
            addPeer(src.id);
        }
        if (getVertexInGraph(sink.id) == null) {
            addPeer(sink.id);
        }
        src = getVertexInGraph(src.id);
        sink = getVertexInGraph(sink.id);
        return new MyReputationEdge(src, sink, id);
    }

    public void unFeedback(MyReputationGraph referenceGraph, int from, int to) {
        if (type == FULL){
            ChatterBox.error(this, "unFeedback()", "This graph is not a dynamic graph.  Illegal method call.");
            return;
        }
        alg.setMatrixFilled(false);
        alg.setIterations(alg.getIterations() - 1);
        for (Agent src: alg.getFeedbackGraph().vertexSet()){
            for (Agent sink : alg.getFeedbackGraph().vertexSet()){
                if (!src.equals(sink)){
                    double trustScore = alg.calculateTrustScore(src, sink);
//                    ChatterBox.print("Trustscore is " + trustScore);
                    if (trustScore == 0.0){
                        ChatterBox.alert("Edge removed.");
                        this.removeEdge(findEdge(src, sink));
                    }else{
                        ((MyReputationEdge)referenceGraph.findEdge(src, sink)).setReputation(trustScore);
                    }
                }
            }
        }

        //remove unnecessary vertices
        ArrayList<Agent> toRemove = new ArrayList<Agent>();
        for (Agent a : getVertices()){
            if (!alg.getFeedbackGraph().vertexSet().contains(a)){
                toRemove.add(a);
            }
        }
        for (Agent a : toRemove){
            removeVertex(a);
        }

    }

    /**
     * Determines how to handle a normal TrustLogEvent
     * @param gev
     * @param forward
     * @param referenceGraph
     */
    public void graphEvent(TrustLogEvent gev, boolean forward, MyGraph referenceGraph) {
        if (type == FULL){
            ChatterBox.error(this, "graphEvent()", "This graph is not a dynamic graph.  Illegal method call.");
            return;
        }
        if (forward){
            feedback((MyReputationGraph)referenceGraph, gev.getAssessor(), gev.getAssessee());
        }else{
            unFeedback((MyReputationGraph) referenceGraph, gev.getAssessor(), gev.getAssessee());
        }
    }

    /**
     * Creates an edge but does not yet add the feedback to it.  As the visible edges, are added, the feedbacks will be added to the hidden edges
     * @param gev	The Log event which needs to be handled.
     */
    public void graphConstructionEvent(TrustLogEvent gev) {
        if (type == DYNAMIC){
            ChatterBox.error(this, "graphConstructionEvent()", "This graph is not a full graph.  Illegal method call.");
            return;
        }
        addPeer(gev.getAssessor());
        addPeer(gev.getAssessee());
        for (Agent src : getVertices()){
            for (Agent sink : getVertices()){
                if (findEdge(src, sink) == null && !(src.equals(sink))){
                    MyReputationEdge edge = createEdge(src, sink);
                    addEdge(edge, src, sink);
                }
            }
        }
    }

    public void printGraph() {
        if (type == FULL) {
            ChatterBox.print("Printing full " + this.getClass().getSimpleName() + "...");
        } else {
            ChatterBox.print("Printing dynamic " + this.getClass().getSimpleName() + "...");
        }
        Collection<Agent> agents = this.getVertices();
        for (Agent a : agents) {
            ChatterBox.print(a.toString());
        }
        Collection<TestbedEdge> edges = getEdges();
        for (TestbedEdge e : edges) {
            ChatterBox.print("Edge: " + e.src + " to " + e.sink + " " + ((MyReputationEdge)e).getReputation());
        }
        ChatterBox.print("Done.");
    }

}
////////////////////////////////////////////////////////////////////////////////

