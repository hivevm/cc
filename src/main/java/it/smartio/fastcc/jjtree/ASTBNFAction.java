// Generated by FastCC v.8.0 - Do not edit this line!

package it.smartio.fastcc.jjtree;

public class ASTBNFAction extends JJTreeNode {

  public ASTBNFAction(JJTreeParser p, int id) {
    super(p, id);
  }

  public Node getScopingParent(NodeScope ns) {
    for (Node n = jjtGetParent(); n != null; n = n.jjtGetParent()) {
      if (n instanceof ASTBNFNodeScope) {
        if (((ASTBNFNodeScope) n).node_scope == ns) {
          return n;
        }
      } else if (n instanceof ASTExpansionNodeScope) {
        if (((ASTExpansionNodeScope) n).node_scope == ns) {
          return n;
        }
      }
    }
    return null;
  }


  @Override
  public final Object jjtAccept(JJTreeParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}
// FastCC Checksum=7C725A0094DFDE1229B6BFE3AA45A633 (Do not edit this line!)
// FastCC Options: NODE_FACTORY='', VISITOR_DATA_TYPE='', VISITOR='true', NODE_CLASS='JJTreeNode',
// NODE_TYPE='ASTBNFAction', VISITOR_RETURN_TYPE_VOID='false', VISITOR_EXCEPTION='',
// PARSER_NAME='JJTreeParser', VISITOR_RETURN_TYPE='Object'
