class JavelinCli < Formula
  desc "Automated Spectrum-Based Fault Localization for Java"
  homepage "https://github.com/DesmondQue/javelin-cli"
  version "1.0.1"
  url "https://github.com/DesmondQue/javelin-cli/releases/download/v1.0.1-beta/javelin-cli-1.0.1.tar"
  sha256 "6bd350c7531aa17bac207deb70955fa2e718406c011c1a8b96016e192ff86131"
  license "MIT"

  on_macos do
    depends_on "openjdk@21"
  end

  def install
    # Remove Windows batch files
    rm Dir["bin/*.bat"]

    libexec.install Dir["*"]

    # Create a wrapper script that uses Homebrew's JDK on macOS, or system Java on Linux
    if OS.mac? && Formula["openjdk@21"].any_version_installed?
      (bin/"javelin").write_env_script libexec/"bin/javelin",
        JAVA_HOME: Formula["openjdk@21"].opt_prefix
    else
      (bin/"javelin").write_env_script libexec/"bin/javelin", {}
    end
  end

  def caveats
    if OS.linux?
      <<~EOS
        javelin requires Java 21+. On Linux, install it via your system package manager:
          sudo apt install openjdk-21-jdk    # Debian/Ubuntu
          sudo dnf install java-21-openjdk   # Fedora
        Then ensure `java -version` reports 21 or higher.
      EOS
    else
      <<~EOS
        javelin requires Java 21+. Homebrew's openjdk@21 is installed automatically.
        If you prefer a different JDK, set JAVA_HOME to its path before running javelin.
      EOS
    end
  end

  test do
    assert_match "Javelin Core", shell_output("#{bin}/javelin --version")
  end
end
