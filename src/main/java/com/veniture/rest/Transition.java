package com.veniture.rest;


import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.issue.index.IssueIndexingService;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.TransitionOptions;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.net.RequestFactory;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.veniture.pojo.IssueTableData;
import com.veniture.RemoteSearcher;
import com.veniture.constants.Constants;
import com.veniture.util.functions;
import org.apache.commons.httpclient.URIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import java.util.Arrays;


@Path("/transition")
public class Transition {
    @JiraImport
    private RequestFactory requestFactory;
    @JiraImport
    private ApplicationProperties applicationProperties;
    private static final Logger logger = LoggerFactory.getLogger(Transition.class);// The transition ID
    public static final Gson GSON = new Gson();
    public static final IssueService ISSUE_SERVICE = ComponentAccessor.getIssueService();
    public static final ApplicationUser CURRENT_USER = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();

    public Transition(RequestFactory requestFactory){
        this.requestFactory = requestFactory;
    }

    @GET
    @Path("/transitionissues")
    public String transitionIssues(@Context HttpServletRequest req, @Context HttpServletResponse resp) {
        IssueService issueService = ComponentAccessor.getIssueService();
        String[] issues = req.getParameterValues("issues");
        String[] action = req.getParameterValues("action");
        ApplicationUser currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        if (action[0].equals("approve")){
            Arrays.stream(issues).forEach(issue->transitionIssue(issueService, currentUser, issueService.getIssue(currentUser, issue).getIssue(), Constants.ApproveWorkflowTransitionId));
        }
        else if (action[0].equals("decline")){
            Arrays.stream(issues).forEach(issue->transitionIssue(issueService, currentUser, issueService.getIssue(currentUser, issue).getIssue(), Constants.DeclineWorkflowTransitionId));
        }
        return "true";
    }

    @GET
    @Path("/getCfValueFromIssue")
    public String getCfValueFromIssue(@Context HttpServletRequest req, @Context HttpServletResponse resp) throws URIException {
        CustomField customField= ComponentAccessor.getCustomFieldManager().getCustomFieldObject(req.getParameterValues("customFieldId")[0]);
        return ISSUE_SERVICE.getIssue(CURRENT_USER,req.getParameterValues("issueKey")[0]).getIssue().getCustomFieldValue(customField).toString();
    }

    @GET
    @Path("/json")
    public String json(@Context HttpServletRequest req, @Context HttpServletResponse resp) throws URIException, IndexException {
        String[] jsontableString = req.getParameterValues("jsontable");
        JsonArray tableAsJsonArray = jsonString2JsonArray(jsontableString[0]);
        int x=0;
        for (JsonElement jsonElement:tableAsJsonArray){
            if (x==0){x++;continue;}
            IssueTableData issueTableData = GSON.fromJson(jsonElement, IssueTableData.class);
            CustomField oncelikBerkCf = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(Constants.öncelikBerkCfId);
            MutableIssue issue = ISSUE_SERVICE.getIssue(CURRENT_USER,issueTableData.getIssueKey()).getIssue();
            com.veniture.util.functions.updateCustomFieldValue(issue,oncelikBerkCf,Double.valueOf(issueTableData.getCompanyPriority()),CURRENT_USER);
        }

       // updateCustomFieldValue("key","asd","value");
        return null;
    }

    private void getAllTeamsfromTempo() throws URIException {
        RemoteSearcher remoteSearcher =  new RemoteSearcher(requestFactory);
        remoteSearcher.search();
        //Request request = requestFactory.createRequest(Request.MethodType.GET, getCurrentAppBaseUrl());
    }

    private JsonArray jsonString2JsonArray(String responseString) {
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(responseString);
        JsonArray jsonArray = jsonElement.getAsJsonArray();
        return jsonArray;
    }

    private void transitionIssue(IssueService issueService, ApplicationUser currentUser, Issue issue, Integer workflowTransitionId) {

        final TransitionOptions transitionOptions = new TransitionOptions.Builder().skipPermissions().skipValidators().setAutomaticTransition().skipConditions().build();

        IssueService.TransitionValidationResult result = issueService.validateTransition(currentUser,
                issue.getId(),
                workflowTransitionId,
                issueService.newIssueInputParameters(),
                transitionOptions);

        if (result.isValid()) {
            issueService.transition(currentUser, result);
        } else {
            logger.error(result.getErrorCollection().toString());
        }
    }

}
