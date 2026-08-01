import os
import re
import sys
import subprocess
from datetime import datetime

def input_with_default(prompt, default):
    try:
        import readline
        readline.set_startup_hook(lambda: readline.insert_text(default))
        try:
            val = input(prompt)
            return val if val else default
        finally:
            readline.set_startup_hook()
    except ImportError:
        val = input(f"{prompt} [{default}]: ").strip().replace("\ufeff", "")
        return val if val else default

# Path constants
ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_GRADLE_PATH = os.path.join(ROOT_DIR, "app", "build.gradle.kts")
CHANGELOG_PATH = os.path.join(ROOT_DIR, "docs", "CHANGELOG.md")
FASTLANE_DIR = os.path.join(ROOT_DIR, "fastlane", "metadata", "android", "en-US", "changelogs")

def parse_version_name(version_name):
    # Regex to extract major.minor.patch.build from name
    match = re.search(r"(\d+)\.(\d+)\.(\d+)\.(\d+)", version_name)
    if not match:
        raise ValueError(f"Could not parse version code from version name '{version_name}'. Format must contain X.Y.Z.B")

    major = int(match.group(1))
    minor = int(match.group(2))
    patch = int(match.group(3))
    build = int(match.group(4))

    # Formula: Major * 100000000 + Minor * 10000000 + Patch * 10000 + Build
    version_code = major * 100000000 + minor * 10000000 + patch * 10000 + build
    return version_code, (major, minor, patch, build)

def suggest_version_name(release_type):
    # Parse current version from build.gradle.kts to get major, minor
    major, minor = "5", "1"
    if os.path.exists(BUILD_GRADLE_PATH):
        with open(BUILD_GRADLE_PATH, "r", encoding="utf-8") as f:
            gradle_content = f.read()
        curr_name_match = re.search(r"versionName\s*=\s*(?:overrideVersionName\s*\?:\s*)?\"(.*?)\"", gradle_content)
        curr_name = curr_name_match.group(1) if curr_name_match else "5.1.412.1078 Beta"

        match = re.search(r"(\d+)\.(\d+)", curr_name)
        if match:
            major = match.group(1)
            minor = match.group(2)

    # Calculate patch (days since May 4, 2025)
    start_date = datetime(2025, 5, 4)
    patch = (datetime.today() - start_date).days

    # Calculate build number (git commit count + offset)
    try:
        commit_count = int(subprocess.check_output(["git", "rev-list", "--count", "HEAD"]).decode("utf-8").strip())
    except Exception:
        commit_count = 985

    try:
        last_tag = subprocess.check_output(
            ["git", "describe", "--tags", "--abbrev=0"],
            stderr=subprocess.DEVNULL
        ).decode("utf-8").strip()

        tag_build_match = re.search(r"\.(\d+)(?:-|$)", last_tag)
        tag_build = int(tag_build_match.group(1)) if tag_build_match else 1078

        tag_commits = int(subprocess.check_output(["git", "rev-list", "--count", last_tag]).decode("utf-8").strip())
        offset = tag_build - tag_commits
    except Exception:
        offset = 96

    build = commit_count + offset
    suffix = " Beta" if release_type == "Beta" else ""
    return f"{major}.{minor}.{patch}.{build}{suffix}"

def extract_unreleased_changelog():
    if not os.path.exists(CHANGELOG_PATH):
        return ""

    with open(CHANGELOG_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    # Handles both escaped (\[Unreleased]) and unescaped ([Unreleased]) bracket styles
    match = re.search(r"##\s*\\?\[Unreleased\](.*?)(?=\n##\s*\\?\[|\Z)", content, re.DOTALL | re.IGNORECASE)
    if not match:
        return ""

    section = match.group(1).strip()
    return section

def get_commits_since_last_tag(previous_tag=None):
    try:
        if not previous_tag:
            # Get the last tag name
            previous_tag = subprocess.check_output(
                ["git", "describe", "--tags", "--abbrev=0"],
                stderr=subprocess.DEVNULL
            ).decode("utf-8").strip()
        log_cmd = ["git", "log", f"{previous_tag}..HEAD", "--oneline"]
        print(f"Fetching commits since tag: {previous_tag}")
    except Exception:
        # Fallback if no tags exist
        log_cmd = ["git", "log", "--oneline"]
        print("No previous tags found. Fetching all commits...")

    try:
        log_output = subprocess.check_output(log_cmd).decode("utf-8").strip()
        if not log_output:
            return []

        commits = []
        has_translation = False
        for line in log_output.splitlines():
            parts = line.split(" ", 1)
            if len(parts) > 1:
                msg = parts[1].strip()
                # Skip merge and release utility commits to keep clean
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
        print(f"Error fetching commits: {e}")
        return []

def format_fastlane_changelog(version_name, changes_text, release_type):
    # Get major.minor
    match = re.search(r"(\d+)\.(\d+)", version_name)
    version_str = f"v{match.group(1)}.{match.group(2)}" if match else ""

    title = f"Rhythm {version_str} - {release_type} Update\n\n"
    footer = "\nThanks for using Rhythm ;)"

    # Parse items — collapse translation entries into one
    items = []
    has_translation = False
    for line in changes_text.splitlines():
        line = line.strip()
        if line.startswith("-") or line.startswith("*") or line.startswith("•"):
            item = re.sub(r"^[-*•]\s*", "", line)
            # Remove markdown links, e.g. [text](url) -> text
            item = re.sub(r"\[(.*?)\].*?", r"\1", item)
            # Remove bold/italic markdown
            item = item.replace("**", "").replace("_", "")
            # Group all translation/l10n lines
            if re.search(r"l10n|translation|localiz|update.*lang", item, re.IGNORECASE):
                has_translation = True
                continue
            items.append(f"• {item}")
    if has_translation:
        items.append("• Updated translations")

    # Assemble with max 500 chars limit (F-Droid restriction)
    content = title
    for item in items:
        # Check if adding this item would exceed limit (reserving space for footer)
        if len(content) + len(item) + 2 + len(footer) <= 500:
            content += item + "\n"
        else:
            note = "• Various bug fixes and improvements.\n"
            if len(content) + len(note) + len(footer) <= 500:
                content += note
            break

    content += footer
    return content

def update_build_gradle(new_version_code, new_version_name):
    with open(BUILD_GRADLE_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    code_pattern = r"(versionCode\s*=\s*(?:overrideVersionCode\s*\?:\s*)?)(\d+)"
    name_pattern = r"(versionName\s*=\s*(?:overrideVersionName\s*\?:\s*)?\")(.*?)(\")"
    date_pattern = r"(overrideReleaseDate\s*\?:\s*\")(\d{4}-\d{2}-\d{2})(\"\})"

    if not re.search(code_pattern, content):
        print("Error: Could not find versionCode pattern in build.gradle.kts")
        sys.exit(1)

    if not re.search(name_pattern, content):
        print("Error: Could not find versionName pattern in build.gradle.kts")
        sys.exit(1)

    if not re.search(date_pattern, content):
        print("Error: Could not find RELEASE_DATE pattern in build.gradle.kts")
        sys.exit(1)

    today_date = datetime.today().strftime('%Y-%m-%d')
    content = re.sub(code_pattern, lambda m: m.group(1) + str(new_version_code), content)
    content = re.sub(name_pattern, lambda m: m.group(1) + new_version_name + m.group(3), content)
    content = re.sub(date_pattern, lambda m: m.group(1) + today_date + m.group(3), content)

    with open(BUILD_GRADLE_PATH, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Updated app/build.gradle.kts with versionCode={new_version_code}, versionName='{new_version_name}', releaseDate='{today_date}'")

def update_changelog_file(new_version_name, raw_unreleased):
    with open(CHANGELOG_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    today = datetime.today().strftime('%Y-%m-%d')
    new_section = f"## [Unreleased]\n\n### Added\n- \n\n## [{new_version_name}] - {today}\n\n{raw_unreleased}\n"

    # Replace [Unreleased] section - handles both escaped (\[) and literal ([) bracket styles
    pattern = r"##\s*\\?\[Unreleased\](.*?)(?=\n##\s*\\?\[|\Z)"

    def repl(m):
        return new_section

    content = re.sub(pattern, repl, content, flags=re.DOTALL | re.IGNORECASE)

    # Normalize: remove any stray backslash-escapes on section headers (e.g. \[ -> [)
    content = re.sub(r"(##\s*)\\\[", r"\1[", content)

    # Write with Unix line endings so git doesn't see spurious CRLF changes
    with open(CHANGELOG_PATH, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)

    print("Updated docs/CHANGELOG.md")


def prompt_banner_choice(is_beta):
    """Prompt the user to choose a release banner option."""
    default_beta_url = "https://github.com/user-attachments/assets/52d9f9b9-722e-4e66-abab-2dcbf59b6648"
    default_stable_url = "https://github.com/user-attachments/assets/f307174a-ec2e-41ec-b274-0a458123d4f7"
    default_url = default_beta_url if is_beta else default_stable_url

    print("\nRelease banner:")
    print("  1. Use predefined banner (current default)")
    print("  2. Enter a custom banner URL")
    print("  3. No banner")
    choice = input("Select banner option [1/2/3] (default: 1): ").strip().replace("\ufeff", "") or "1"

    if choice == "2":
        url = input("Enter the full image URL for the banner: ").strip().replace("\ufeff", "")
        return url if url else default_url
    elif choice == "3":
        return None
    else:
        return default_url

def main():
    print("=== Rhythm Release Preparation Tool ===")

    # Prompt release type
    release_type = input("Enter release type [1: Stable, 2: Beta] (default: Stable): ").strip().replace("\ufeff", "")
    if release_type == "2":
        release_type = "Beta"
    else:
        release_type = "Stable"

    is_beta = release_type == "Beta"

    # Get current versionName from build.gradle.kts to show as reference
    with open(BUILD_GRADLE_PATH, "r", encoding="utf-8") as f:
        gradle_content = f.read()
    curr_name_match = re.search(r"versionName\s*=\s*(?:overrideVersionName\s*\?:\s*)?\"(.*?)\"", gradle_content)
    curr_name = curr_name_match.group(1) if curr_name_match else "unknown"
    print(f"Current version in build.gradle.kts: {curr_name}")

    # Suggest version name based on date-derived patch and commit count build
    suggested_name = suggest_version_name(release_type)
    new_version_name = input_with_default("Enter the new version name", suggested_name)
    new_version_name = new_version_name.replace("\\", "").strip()

    try:
        new_version_code, (major, minor, patch, build) = parse_version_name(new_version_name)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

    print(f"New Version Code: {new_version_code}")

    # Banner choice
    banner_url = prompt_banner_choice(is_beta)

    # Prompt for changelog source
    print("\nChangelog source:")
    print("  1. Git commits since last tag (Recommended - Real changes)")
    print("  2. Read from docs/CHANGELOG.md [Unreleased] section (Fallback)")
    source_choice = input("Select source [1 or 2] (default: 1): ").strip().replace("\ufeff", "")

    raw_unreleased = ""
    if source_choice == "2":
        raw_unreleased = extract_unreleased_changelog()
        has_notes = False
        for line in raw_unreleased.splitlines():
            line = line.strip()
            if (line.startswith("-") or line.startswith("*") or line.startswith("•")) and len(line) > 2:
                has_notes = True
                break
        if not has_notes:
            print("No notes found in docs/CHANGELOG.md. Falling back to Git commits...")
            source_choice = "1"

    if source_choice != "2":
        # Get recent tags to prompt the user
        try:
            tags_output = subprocess.check_output(
                ["git", "tag", "--sort=-v:refname"],
                stderr=subprocess.DEVNULL
            ).decode("utf-8").strip().splitlines()
            recent_tags = [t.strip() for t in tags_output if t.strip()][:5]
        except Exception:
            recent_tags = []

        selected_tag = None
        if recent_tags:
            print("\nSelect the previous tag to generate changelog from:")
            for idx, tag in enumerate(recent_tags):
                print(f"  {idx + 1}. {tag}")
            print(f"  {len(recent_tags) + 1}. Custom tag name / branch / commit")

            choice = input(f"Select option [1-{len(recent_tags) + 1}] (default: 1): ").strip().replace("\ufeff", "")
            if not choice:
                selected_tag = recent_tags[0]
            elif choice.isdigit() and 1 <= int(choice) <= len(recent_tags):
                selected_tag = recent_tags[int(choice) - 1]
            elif choice.isdigit() and int(choice) == len(recent_tags) + 1:
                selected_tag = input("Enter custom tag name or commit/branch: ").strip().replace("\ufeff", "")
            else:
                # User typed a tag/branch/commit name directly
                selected_tag = choice

        commits = get_commits_since_last_tag(selected_tag)
        if commits:
            print(f"Found {len(commits)} commits.")
            raw_unreleased = "### Added\n" + "\n".join([f"- {c}" for c in commits])
        else:
            print("No commits found.")
            raw_unreleased = "### Added\n- Minor bug fixes and performance improvements."

    # Format Fastlane changelog
    fastlane_content = format_fastlane_changelog(new_version_name, raw_unreleased, release_type)

    # Print Fastlane preview
    print("\n--- Fastlane Changelog Preview ---")
    print(fastlane_content)
    print(f"Total Characters: {len(fastlane_content)}/500")
    print("---------------------------------\n")

    # Show banner preview
    if banner_url:
        print(f"Banner URL: {banner_url}")
    else:
        print("Banner: None (no image will be included)")
    print()

    confirm = input("Does this look correct? Proceed with file modifications? (y/N): ").strip().lower().replace("\ufeff", "")
    if confirm != 'y':
        print("Aborted.")
        sys.exit(0)

    # Modify build.gradle.kts
    update_build_gradle(new_version_code, new_version_name)

    # Modify docs/CHANGELOG.md
    update_changelog_file(new_version_name, raw_unreleased)

    # Write Fastlane file
    os.makedirs(FASTLANE_DIR, exist_ok=True)
    fastlane_file_path = os.path.join(FASTLANE_DIR, f"{new_version_code}.txt")
    with open(fastlane_file_path, "w", encoding="utf-8") as f:
        f.write(fastlane_content)
    print(f"Created Fastlane changelog: {fastlane_file_path}")

    # Store banner URL choice so generate_release_notes.py can pick it up
    banner_hint_path = os.path.join(ROOT_DIR, ".release_banner_url")
    if banner_url:
        with open(banner_hint_path, "w", encoding="utf-8") as f:
            f.write(banner_url)
        print(f"Stored banner URL: .release_banner_url")
    else:
        # Clear any previous stored banner
        if os.path.exists(banner_hint_path):
            os.remove(banner_hint_path)
        print("No banner selected.")

    normalized_tag = f"v{new_version_name.lower().replace(' ', '-')}"
    print("\nRelease files prepared successfully!")
    print("\nTo build and release, run:")
    print("  git add app/build.gradle.kts docs/CHANGELOG.md fastlane/metadata/android/en-US/changelogs/")
    print(f"  git commit -m \"Release {new_version_name}\"")
    print(f"  git tag {normalized_tag}")
    print("  git push origin main --tags")

if __name__ == "__main__":
    main()
