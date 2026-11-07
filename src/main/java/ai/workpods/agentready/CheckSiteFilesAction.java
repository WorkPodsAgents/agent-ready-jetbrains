package ai.workpods.agentready;

final class CheckSiteFilesAction extends CheckAction {
    CheckSiteFilesAction() {
        super("check_robots_sitemap_llms_txt", "Site whose robots.txt, sitemaps, llms.txt and agent files to read:",
                "Reading the site files");
    }
}
