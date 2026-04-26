class JavelinCli < Formula
  desc "Automated Spectrum-Based Fault Localization for Java"
  homepage "https://github.com/DesmondQue/javelin-cli"
  version "1.1.1-beta"
  url "https://github.com/DesmondQue/javelin-cli/releases/download/v1.1.1-beta/javelin-cli-1.1.1-beta.tar"
  sha256 "22ad908e24484c04731967e904ff6149b1f69794743db89ac2320566fd2c0689"
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
