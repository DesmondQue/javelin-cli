class JavelinCli < Formula
  desc "Automated Spectrum-Based Fault Localization for Java"
  homepage "https://github.com/DesmondQue/javelin-core-cli"
  version "1.0.0"
  url "https://github.com/DesmondQue/javelin-core-cli/releases/download/v#{version}/javelin-cli-#{version}.tar"
  sha256 "36b7a22d41f19a0856138ce83f4037ae407b8f4c83800dc2648b9f65558ac87d"
  license "MIT"

  depends_on "openjdk@21" => :optional
  depends_on java: "21+"

  def install
    # Remove Windows batch files
    rm Dir["bin/*.bat"]

    libexec.install Dir["*"]

    # Create a wrapper script; use the system Java if JAVA_HOME is already set
    env = if Formula["openjdk@21"].any_version_installed?
      { JAVA_HOME: Formula["openjdk@21"].opt_prefix }
    else
      {}
    end
    (bin/"javelin").write_env_script libexec/"bin/javelin", env
  end

  test do
    assert_match "Javelin Core", shell_output("#{bin}/javelin --version")
  end
end
