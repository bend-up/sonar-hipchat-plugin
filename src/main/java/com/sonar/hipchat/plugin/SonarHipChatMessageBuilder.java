package com.sonar.hipchat.plugin;

import java.io.StringWriter;
import java.util.List;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.Severity;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.sonar.hipchat.plugin.model.Notification;
import com.sonar.hipchat.plugin.model.Notification.NotificationColor;

final class SonarHipChatMessageBuilder implements HipChatMessageBuilder {
	private final Project project;
	private final ProjectIssues projectIssues;
	private final Settings settings;

	SonarHipChatMessageBuilder(Project project, Settings settings, ProjectIssues projectIssues) {
		this.project = project;
		this.projectIssues = projectIssues;
		this.settings = settings;
	}

	@Override
	public String getPreNotificationMessage(String preMessageTemplate) {
		String preMessage = preMessageTemplate.replace("{project}", project.getName()).replace("{date}",
				project.getAnalysisDate().toString());
		return createJsonMessage(preMessage, NotificationColor.gray);
	}

	private String createJsonMessage(String message, NotificationColor backgroundColor) {
		Notification notification = new Notification();
		notification.setColor(backgroundColor);
		notification.setMessage(message);
		return new Gson().toJson(notification);
	}

	@Override
	public String getStatusMessage() {
		List<Issue> issues = Lists.newArrayList(projectIssues.issues());
		long issuesNew = issues.stream().filter(i -> i.isNew()).count();
		// TODO create counters for all severities with {@link
		// java.util.stream.Collectors} for both new and all issues
		long issuesNewBlockers = issues.stream().filter(i -> i.isNew() && i.severity().equals(Severity.BLOCKER))
				.count();
		long issuesBlockers = issues.stream().filter(i -> i.severity().equals(Severity.BLOCKER)).count();
		List<Issue> issuesResolved = Lists.newArrayList(projectIssues.resolvedIssues());

		Velocity.init();

		VelocityContext context = new VelocityContext();
		context.put("projectName", project.getName());
		context.put("analysisDate", project.getAnalysisDate().toString());
		context.put("issuesNew", issuesNew);
		context.put("issuesResolved", issuesResolved.size());
		context.put("issuesTotal", issues.size());
		context.put("issuesNewBlockers", issuesNewBlockers);
		context.put("issuesBlockers", issuesBlockers);

		String template = getTemplate();
		StringWriter writer = new StringWriter();
		Velocity.evaluate(context, writer, "TemplateName", template);

		NotificationColor color = determineNotificationColor(issuesResolved.size(), issuesNew, issues.size(), issuesBlockers);
		return createJsonMessage(writer.toString(), color);
	}

	private String getTemplate() {
		return settings.getString(SonarHipChatProperties.MESSAGE_TEMPLATE);
	}

	private NotificationColor determineNotificationColor(int resolved, long newIssues, int totalIssues, long totalBlockers) {
		if ( totalBlockers > 0 ) {
			return NotificationColor.red;
		} else if (newIssues > 0 ) {
			return NotificationColor.red;
		} else if ( totalIssues > 0 ) {
			return NotificationColor.yellow;
		}
		return NotificationColor.green;
	}
}
