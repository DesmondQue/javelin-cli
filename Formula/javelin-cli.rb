class JavelinCli < Formula
  desc "Automated Spectrum-Based Fault Localization for Java"
  homepage "https://github.com/DesmondQue/javelin-cli"
  version "1.1.0"
  url "https://github.com/DesmondQue/javelin-cli/releases/download/v1.1.0/javelin-cli-1.1.0.tar"
  sha256 "ae3edb56f90d59f4dd80ca0b6ec01ec55577094da416499a334b677fb7abe491"
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
