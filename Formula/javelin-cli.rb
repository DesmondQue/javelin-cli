class JavelinCli < Formula
  desc "Automated Spectrum-Based Fault Localization for Java"
  homepage "https://github.com/DesmondQue/javelin-cli"
  version "1.4.0-beta"
  url "https://github.com/DesmondQue/javelin-cli/releases/download/v1.4.0-beta/javelin-cli-1.4.0-beta.tar"
  sha256 "d25d8a6231a121e7d47126679c936e2ee51f453f6664c90017ed93801c7d8cfb"
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
