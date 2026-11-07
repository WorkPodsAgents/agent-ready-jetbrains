package ai.workpods.agentready;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.testFramework.LightVirtualFile;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Properties;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/** Asks for a URL, runs one Agent Ready check in the background and shows the answer. */
abstract class CheckAction extends AnAction {
    private static final Pattern URL = Pattern.compile("^https?://[^\\s/]+\\.\\S+$");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String tool;
    private final String prompt;
    private final String progress;

    CheckAction(String tool, String prompt, String progress) {
        this.tool = tool;
        this.prompt = prompt;
        this.progress = progress;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;
        String url = Messages.showInputDialog(project, prompt, "Agent Ready Website Audit", null,
                "https://", new InputValidator() {
                    @Override
                    public boolean checkInput(String input) {
                        return URL.matcher(input.trim()).matches();
                    }

                    @Override
                    public boolean canClose(String input) {
                        return checkInput(input);
                    }
                });
        if (url == null) return;
        String target = url.trim();
        AgentReadyClient client = new AgentReadyClient(HTTP, pluginVersion());
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Agent Ready Website Audit: " + progress, true) {
            private CheckResult found;
            private String failure;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    found = client.run(tool, target, indicator);
                } catch (AgentReadyClient.CheckFailed error) {
                    failure = error.getMessage();
                }
            }

            @Override
            public void onSuccess() {
                if (failure != null) {
                    notification(project, failure, NotificationType.ERROR).notify(project);
                } else if (found != null) {
                    show(project, found);
                }
            }
        });
    }

    private static void show(Project project, CheckResult found) {
        Notification notification = notification(project, found.summary(), NotificationType.INFORMATION);
        String reportUrl = found.reportUrl();
        if (reportUrl != null) {
            notification.addAction(NotificationAction.createSimpleExpiring("Open Report",
                    () -> BrowserUtil.browse(reportUrl)));
        }
        notification.addAction(NotificationAction.createSimple("Show Details",
                () -> openDetails(project, found)));
        notification.notify(project);
    }

    private static Notification notification(Project project, String text, NotificationType type) {
        return NotificationGroupManager.getInstance()
                .getNotificationGroup("Agent Ready")
                .createNotification("Agent Ready Website Audit", text, type);
    }

    private static void openDetails(Project project, CheckResult found) {
        String pretty = new GsonBuilder().setPrettyPrinting().create()
                .toJson(JsonParser.parseString(found.resultJson()));
        LightVirtualFile file = new LightVirtualFile("agent-ready-result.json",
                FileTypeManager.getInstance().getFileTypeByExtension("json"), pretty);
        file.setWritable(false);
        new OpenFileDescriptor(project, file).navigate(true);
    }

    /** Written into agent-ready.properties by the build, so no IDE API is needed to read it. */
    private static String pluginVersion() {
        Properties properties = new Properties();
        try (InputStream in = CheckAction.class.getResourceAsStream("/agent-ready.properties")) {
            if (in != null) properties.load(in);
        } catch (IOException ignored) {
            // The version only labels requests; the check works without it.
        }
        return properties.getProperty("version", "unknown");
    }
}
