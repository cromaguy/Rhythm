import os
import re
import sys
import subprocess

# Path constants
ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHANGELOG_PATH = os.path.join(ROOT_DIR, "docs", "CHANGELOG.md")
BUILD_GRADLE_PATH = os.path.join(ROOT_DIR, "app", "build.gradle.kts")
BANNER_HINT_PATH = os.path.join(ROOT_DIR, ".release_banner_url")

DEFAULT_BETA_BANNER = "https://github.com/user-attachments/assets/52d9f9b9-722e-4e66-abab-2dcbf59b6648"
DEFAULT_STABLE_BANNER = "https://github.com/user-attachments/assets/f307174a-ec2e-41ec-b274-0a458123d4f7"

def parse_gradle_version_info():
    version_name = "Unknown"
    version_code = "Unknown"
    if os.path.exists(BUILD_GRADLE_PATH):
        with open(BUILD_GRADLE_PATH, "r", encoding="utf-8") as f:
            content = f.read()
        name_match = re.search(r"versionName\s*=\s*(?:overrideVersionName\s*\?:\s*)?\"(.*?)\"", content)
        if name_match:
            version_name = name_match.group(1)
        code_match = re.search(r"versionCode\s*=\s*(?:overrideVersionCode\s*\?:\s*)?(\d+)", content)
        if code_match:
            version_code = code_match.group(1)
    return version_name, version_code

def extract_release_notes(tag_name):
    # Normalize tag name, e.g. "v5.1.412.1078-beta" -> "5.1.412.1078"
    version_numbers = re.search(r"(\d+\.\d+\.\d+\.\d+)", tag_name)
    if not version_numbers:
        version_numbers = re.search(r"(\d+\.\d+\.\d+)", tag_name)
        
    if not version_numbers:
        print(f"Could not parse version numbers from tag: {tag_name}")
        return ""
        
    version_str = version_numbers.group(1)
    
    if not os.path.exists(CHANGELOG_PATH):
        print(f"Changelog file not found at: {CHANGELOG_PATH}")
        return ""
        
    with open(CHANGELOG_PATH, "r", encoding="utf-8") as f:
        content = f.read()
        
    version_esc = re.escape(version_str)
    pattern = rf"##\s*\[\s*v?{version_esc}.*?\][^\n]*\n(.*?)(?=\n##\s*\[|\Z)"
    
    match = re.search(pattern, content, re.DOTALL | re.IGNORECASE)
    if match:
        return match.group(1).strip()
    return ""


def load_banner_url(is_beta):
    """Load banner URL from .release_banner_url hint file, or return the default."""
    if os.path.exists(BANNER_HINT_PATH):
        with open(BANNER_HINT_PATH, "r", encoding="utf-8") as f:
            url = f.read().strip()
        # Empty file means "no banner"
        return url if url else None
    # No hint file — use defaults
    return DEFAULT_BETA_BANNER if is_beta else DEFAULT_STABLE_BANNER


def render_banner_html(banner_url):
    """Return an HTML snippet for the banner with rounded corners."""
    if not banner_url:
        return None
    return (
        '<p align="center">\n'
        f'  <img src="{banner_url}" alt="Release Banner"\n'
        '       style="border-radius: 16px; width: 100%; max-width: 960px;" />\n'
        '</p>'
    )

def clean_changelog_content(raw_notes):
    lines = raw_notes.splitlines()
    cleaned_items = []
    current_category = "Added"
    has_translation = False
    
    for line in lines:
        line = line.strip()
        if not line:
            continue
            
        cat_match = re.match(r"^###\s*(.*)", line)
        if cat_match:
            current_category = cat_match.group(1).strip()
            continue
            
        if line.startswith("-") or line.startswith("*") or line.startswith("•"):
            item_text = re.sub(r"^[-*•]\s*", "", line)
            if item_text and item_text != "-":
                # Collapse all translation/l10n lines into one
                if re.search(r"l10n|translation|localiz|update.*lang", item_text, re.IGNORECASE):
                    has_translation = True
                    continue
                cleaned_items.append(f"- **{current_category}:** {item_text}")

    if has_translation:
        cleaned_items.append("- **Added:** Updated translations")
                
    return cleaned_items

def get_commits_between_tags(current_tag, previous_tag=None):
    try:
        if not previous_tag:
            # Get list of tags sorted by version
            tags_output = subprocess.check_output(
                ["git", "tag", "--sort=-v:refname"],
                stderr=subprocess.DEVNULL
            ).decode("utf-8").strip().splitlines()
            
            tags = [t.strip() for t in tags_output if t.strip()]
            
            if current_tag in tags:
                idx = tags.index(current_tag)
                # Find the next older tag
                if idx + 1 < len(tags):
                    previous_tag = tags[idx + 1]
                
        if previous_tag:
            log_cmd = ["git", "log", f"{previous_tag}..{current_tag}", "--oneline"]
            print(f"Fetching commits between {previous_tag} and {current_tag}")
        else:
            log_cmd = ["git", "log", f"{current_tag}", "--oneline"]
            print(f"Fetching all commits up to {current_tag}")
            
        log_output = subprocess.check_output(log_cmd).decode("utf-8").strip()
        if not log_output:
            return []
            
        commits = []
        has_translation = False
        for line in log_output.splitlines():
            parts = line.split(" ", 1)
            if len(parts) > 1:
                msg = parts[1].strip()
                if msg.startswith("Merge branch") or msg.startswith("Merge pull request") or msg.startswith("Release "):
                    continue
                # Collapse all translation/l10n commits into one line
                if re.search(r"l10n|translation|chore\(l10n\)|update.*translation|localiz", msg, re.IGNORECASE):
                    has_translation = True
                    continue
                commits.append(msg)
        if has_translation:
            commits.append("Updated translations")
        return commits
    except Exception as e:
        print(f"Error fetching commits between tags: {e}")
        return []

def main():
    if len(sys.argv) < 2:
        print("Usage: python generate_release_notes.py <tag_name> [commit_sha] [previous_tag]")
        sys.exit(1)
        
    tag_name = sys.argv[1]
    commit_sha = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("GITHUB_SHA", "unknown")
    custom_prev_tag = sys.argv[3] if len(sys.argv) > 3 else None
    
    is_beta = "beta" in tag_name.lower() or "alpha" in tag_name.lower() or "rc" in tag_name.lower()
    
    print(f"Generating release notes for tag: {tag_name} (IsBeta={is_beta})")
    raw_notes = extract_release_notes(tag_name)
    
    bullets = []
    if raw_notes:
        bullets = clean_changelog_content(raw_notes)
        if not bullets:
            # Fallback to general bullet lines if no categories matched
            for line in raw_notes.splitlines():
                line = line.strip()
                if line.startswith("-") or line.startswith("*") or line.startswith("•"):
                    bullets.append(line)
                    
    if not bullets:
        print("No changelog entries found in docs/CHANGELOG.md. Fetching commits since the previous tag...")
        commits = get_commits_between_tags(tag_name, custom_prev_tag)
        if commits:
            bullets = [f"- **Added:** {c}" for c in commits]
        else:
            bullets = ["- **Added:** Minor bug fixes and performance improvements."]
        
    version_name, version_code = parse_gradle_version_info()
    
    major_minor = "5.1"
    mm_match = re.search(r"(\d+\.\d+)", tag_name)
    if mm_match:
        major_minor = mm_match.group(1)
    
    build_num = "1078"
    build_match = re.search(r"\.(\d+)(?:-|$)", tag_name)
    if build_match:
        build_num = build_match.group(1)
    elif version_name != "Unknown":
        parts = version_name.split(" ")[0].split(".")
        if len(parts) >= 4:
            build_num = parts[3]
            
    github_notes = []
    
    # Load banner URL (respects prepare_release.py choice, or uses default)
    banner_url = load_banner_url(is_beta)
    banner_html = render_banner_html(banner_url)

    # Title & Banner
    if is_beta:
        github_notes.append(f"# Rhythm {major_minor} - Bug Fix Update\n")
    else:
        github_notes.append(f"# Rhythm {major_minor} - Feature Update\n")

    if banner_html:
        github_notes.append(banner_html + "\n")
        
    # What's New section
    github_notes.append("**What's New:**")
    for bullet in bullets:
        github_notes.append(bullet)
    github_notes.append("- **Many more reported Bug Fixes, UI & Performance Improvements.**")
    
    github_notes.append("")
    
    # Known Issues section
    if is_beta:
        github_notes.append("**Known Issues (Will be fixed on a later build):**")
        github_notes.append("   - Translation contributions are being collected.")
        github_notes.append("   - Report to GitHub Issues or Community on Discord & Telegram.")
    else:
        github_notes.append("**Known Issues:**")
        github_notes.append("   - Translation contributions are being collected.")
        github_notes.append("   - Report to GitHub Issues or Community on Discord & Telegram.")
        
    github_notes.append("")
    
    # Build Info section
    github_notes.append("**Build Information:**")
    github_notes.append(f"- Build: {build_num}")
    github_notes.append(f"- Type: {'Beta' if is_beta else 'Stable'} Release")
    
    github_notes.append("\n---\n")
    
    # Important update notes
    github_notes.append("> [!NOTE]")
    github_notes.append("> **Important Update Notes**")
    if is_beta:
        github_notes.append("> * Don't restore old backups")
        github_notes.append("\n> [!CAUTION]")
        github_notes.append("> This is a **Beta** build. Please report [Issues](https://github.com/cromaguy/Rhythm/issues) if found.\n")
    else:
        github_notes.append("> * The app is in active development, so many features might be missing compared to other FOSS players.")
        github_notes.append("> * You will find several improvements and changes with each release, so please stay up to date.")
        github_notes.append("\n> [!TIP]")
        github_notes.append("> * **Turn on Auto-Backup** so that no matter what happens, you can always recover your data.")
        github_notes.append("> * You can turn off/on/manage APIs based on your needs from **Settings**.\n")
        
    # Badges Table
    github_notes.append('<div align="center">\n')
    github_notes.append('| | | | | |')
    github_notes.append('|:---:|:---:|:---:|:---:|:---:|')
    github_notes.append('| [<img src="https://github.com/user-attachments/assets/7ec1bd6a-7258-42ae-a264-12bbb51917be" alt="F-Droid" height="35">](https://f-droid.org/packages/chromahub.rhythm.app) | [<img src="https://github.com/user-attachments/assets/d3a1dfc4-e192-418a-8f5b-e21eaf6ba194" alt="IzzyOnDroid" height="35">](https://apt.izzysoft.de/fdroid/index/apk/chromahub.rhythm.app) | [<img src="https://github.com/user-attachments/assets/c479857e-83ab-4709-be48-d221321a4559" alt="Download APK" height="35">](https://github.com/cromaguy/Rhythm/releases/latest) | [<img src="https://github.com/user-attachments/assets/291f5586-b98f-4991-ac03-9a4eae1db159" alt="OpenAPK" height="35">](https://www.openapk.net/rhythm/chromahub.rhythm.app/) | [<img src="https://github.com/user-attachments/assets/22adc0a8-52b1-4977-9e6f-cbca95b14eec" alt="Obtainium" height="35">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/cromaguy/Rhythm/) |')
    github_notes.append('\n</div>\n')
    
    github_notes.append("---")
    
    # Sponsors and credits
    github_notes.append("\nIf you enjoy **Rhythm** and want to support open-source development:\n")
    github_notes.append("[![Support me](https://github.com/user-attachments/assets/434466be-fd50-41e5-8bc4-38fcbb57ee17)](https://ko-fi.com/anjishnunandi)\n")
    github_notes.append("* **Patreon:** [patreon.com/AnjishnuNandi](https://patreon.com/AnjishnuNandi)")
    github_notes.append("* **GitHub Sponsors:** [github.com/sponsors/cromaguy](https://github.com/sponsors/cromaguy)\n")
    github_notes.append("### 🏆 Special Credits")
    github_notes.append("* **The Community**: A big thanks to all Testers, Contributors, and Users!")
    github_notes.append("\n---\n")
    
    release_notes_content = "\n".join(github_notes)
    
    output_path = os.path.join(ROOT_DIR, "release_notes.md")
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(release_notes_content)
        
    print(f"Generated release notes file at: {output_path}")
    
if __name__ == "__main__":
    main()
