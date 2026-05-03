class JavelinCli < Formula
  desc "Automated Spectrum-Based Fault Localization for Java"
  homepage "https://github.com/DesmondQue/javelin-cli"
  version "1.5.0-beta"
  url "https://github.com/DesmondQue/javelin-cli/releases/download/v1.5.0-beta/javelin-cli-1.5.0-beta.tar"
  sha256 "bd81a272b8d316218239b905bb9d8e872b9b4471bff8e07056c3cde86a6f452d"
  license "MIT"

  def install
    # Remove Windows batch files
    rm Dir["bin/*.bat"]

    libexec.install Dir["*"]

    (bin/"javelin").write_env_script libexec/"bin/javelin", {}
  end

  def caveats
    <<~EOS
      javelin requires Java 21+. Install it via your package manager:
        brew install openjdk@21              # macOS (Homebrew)
        sudo apt install openjdk-21-jdk      # Debian/Ubuntu
        sudo dnf install java-21-openjdk     # Fedora
      Then ensure `java -version` reports 21 or higher.
    EOS
  end

  test do
    assert_match "Javelin Core", shell_output("#{bin}/javelin --version")
  end
end
