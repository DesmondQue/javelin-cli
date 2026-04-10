class JavelinCli < Formula
  desc "Automated Spectrum-Based Fault Localization for Java"
  homepage "https://github.com/DesmondQue/javelin-cli"
  version "1.0.0"
  url "https://github.com/DesmondQue/javelin-cli/releases/download/v1.0.0-beta/javelin-cli-1.0.0.tar"
  sha256 "68d258d7d5d548b370c5292ef97129143370dbd70b5f2600fb3cfd91b7d79d2a"
  license "MIT"

  depends_on "openjdk@21"

  def install
    # Remove Windows batch files
    rm Dir["bin/*.bat"]

    libexec.install Dir["*"]

    # Create a wrapper script pointing JAVA_HOME to the Homebrew-managed JDK
    (bin/"javelin").write_env_script libexec/"bin/javelin",
      JAVA_HOME: Formula["openjdk@21"].opt_prefix
  end

  def caveats
    <<~EOS
      javelin requires Java 21+. Homebrew's openjdk@21 is installed automatically.
      If you prefer a different JDK, set JAVA_HOME to its path before running javelin.
    EOS
  end

  test do
    assert_match "Javelin Core", shell_output("#{bin}/javelin --version")
  end
end
