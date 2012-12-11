/**
 * Copyright (C) ${year} eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.intranetExtras.jobs;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.ecm.webui.utils.PermissionUtil;
import org.exoplatform.ecm.webui.utils.Utils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.exoplatform.services.cms.documents.TrashService;
import org.exoplatform.services.cms.folksonomy.NewFolksonomyService;
import org.exoplatform.services.cms.relations.RelationsService;
import org.exoplatform.services.cms.actions.ActionServiceContainer;
import org.exoplatform.services.cms.taxonomy.TaxonomyService;
import org.exoplatform.services.cms.thumbnail.ThumbnailService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;

import java.util.Calendar;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.ReferentialIntegrityException;

public class CleanerTrashJob implements Job {
	
  private static final Log LOG = ExoLogger.getLogger(CleanerTrashJob.class);
  
  public CleanerTrashJob() {}
	  
  public void execute(JobExecutionContext context) throws JobExecutionException {
    String timeLimit = System.getProperty("valid.time.in.trash");
    if (timeLimit == null) timeLimit = "30";
    TrashService trashService = (TrashService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(TrashService.class);
    Node trashNode = trashService.getTrashHomeNode();
    try {
      if (trashNode.hasNodes()){
        NodeIterator childNodes = trashNode.getNodes();
        while (childNodes.hasNext()){
    	  Node currentNode = (Node) childNodes.next();
    	  try {
    	    LOG.info("Removing " + currentNode.getName() + " node from Trash");
    	    if (currentNode.hasProperty("exo:lastModifiedDate")) {
    	      long dateCreated = currentNode.getProperty("exo:lastModifiedDate").getDate().getTimeInMillis();
    	      if ((Calendar.getInstance().getTimeInMillis() - dateCreated > Long.parseLong(timeLimit)*24*60*60*1000) && (currentNode.isNodeType("exo:restoreLocation"))){
    		    deleteNode(currentNode, trashService);
    	      }
    	    } else {
              deleteNode(currentNode, trashService);
    	    }
    	  } catch(Exception ex){
		    LOG.info("Error while removing " + currentNode.getName() + " node from Trash", ex);
		  }
        }
      }
    } catch (RepositoryException ex){
      LOG.info("Failed to get child nodes", ex);
    }
    LOG.info("Empty Trash folder successfully!");
  }
  
  private static void removeMixins(Node node) throws Exception {
	NodeType[] mixins = node.getMixinNodeTypes();
	for (NodeType nodeType : mixins) {
	  node.removeMixin(nodeType.getName());
	}
  }  
  
  public void deleteNode(Node node, TrashService trashService) throws Exception{
	TaxonomyService taxonomyService = (TaxonomyService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(TaxonomyService.class);
	ActionServiceContainer actionService = (ActionServiceContainer) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ActionServiceContainer.class);
	ThumbnailService thumbnailService = (ThumbnailService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ThumbnailService.class);
	RepositoryService repoService = (RepositoryService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(RepositoryService.class);
    RelationsService relationService = (RelationsService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(RelationsService.class);
	Session session = node.getSession();
	Node parentNode = node.getParent();
	try{
	  try{
        PropertyIterator iter = node.getReferences();
        while (iter.hasNext()){
          Node refNode = iter.nextProperty().getParent();
          relationService.removeRelation(refNode, node.getPath());
        }
	  } catch(Exception ex){
	  	LOG.info("An error occurs while removing relations", ex);
	  }
	  try{
	  	taxonomyService.removeTaxonomyNode(session.getWorkspace().getName(), node.getPath());
	  } catch(Exception ex){
	  	LOG.info("An error occurs while removing taxonomy", ex);
	  }
	  try{
	    if (node.isNodeType(Utils.RMA_RECORD)) removeMixins(node);
	  } catch(Exception ex){
	  	LOG.info("An error occurs while removing mixins ", ex);
	  }
	  try{
	  	actionService.removeAction(node, repoService.getCurrentRepository().getConfiguration().getName());
	  } catch(Exception ex){
	  	LOG.info("An error occurs while removing actions ", ex);
	  }
	  try {
	  	thumbnailService.processRemoveThumbnail(node);
	  } catch(Exception ex){
	  	LOG.info("An error occurs while removing thumbnail ", ex);
	  }
	  try{
        if (PermissionUtil.canRemoveNode(node) && node.isNodeType(Utils.EXO_AUDITABLE)) {
	      removeAuditForNode(node, repoService.getCurrentRepository());
	    }
	  } catch(Exception ex){
	  	LOG.info("An error occurs while removing audit ", ex);
	  }
	  node.remove();
	  parentNode.save();
	} catch(ReferentialIntegrityException ref){
	  session.refresh(false);
	} catch (ConstraintViolationException cons) {
	  session.refresh(false);
	} catch(Exception ex){
	  LOG.info("Error while removing " + node.getName() + " node from Trash", ex);
	}
	return;
  }
  
  private void removeAuditForNode(Node node, ManageableRepository repository) throws Exception {
	Session session = SessionProvider.createSystemProvider().getSession(node.getSession().getWorkspace().getName(), repository);
    if (session.getRootNode().hasNode("exo:audit") &&
      session.getRootNode().getNode("exo:audit").hasNode(node.getUUID())) {
      session.getRootNode().getNode("exo:audit").getNode(node.getUUID()).remove();
      session.save();
	}
  }
}
