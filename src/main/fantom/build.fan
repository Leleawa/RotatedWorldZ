using build

class Build : BuildPod
{
  new make()
  {
    podName = "rwSpot"
    summary = "RotatedWorldZ: safe spot search"
    version = Version("1.0")
    depends = ["sys 1.0"]
    srcDirs = [`fan/`]
  }
}
