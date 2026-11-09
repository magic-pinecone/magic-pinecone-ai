#!/usr/bin/env python3
"""Enable the local Siri AI build only when the selected Xcode has its APIs."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--disable", action="store_true", help="Return to the normal deployment target.")
    parser.add_argument("--team", help="Development team for this local build, without changing the app bundle identifier.")
    args = parser.parse_args()
    if args.team and not re.fullmatch(r"[A-Z0-9]{10}", args.team):
        parser.error("--team must be a 10-character Apple development team identifier")
    root = Path(__file__).resolve().parents[1]
    config = root / "iosApp/Configuration/SiriAI.local.xcconfig"
    if args.disable:
        config.unlink(missing_ok=True)
        print("Siri AI preview disabled. The normal build remains available.")
        return 0

    developer = Path(os.environ.get("DEVELOPER_DIR") or subprocess.check_output(["xcode-select", "-p"], text=True).strip())
    platform = developer / "Platforms/iPhoneOS.platform/Developer"
    sdk = Path(subprocess.check_output(["xcrun", "--sdk", "iphoneos", "--show-sdk-path"], text=True).strip())
    interfaces = sdk / "System/Library/Frameworks/AppIntents.framework/Modules/AppIntents.swiftmodule"
    has_tools = any("protocol ToolIntent" in path.read_text() for path in interfaces.glob("*.swiftinterface"))
    has_evaluations = (platform / "Library/Frameworks/SiriEvaluations.framework").exists()
    if not has_tools or not has_evaluations:
        print(
            "Siri AI preview requires an Xcode SDK containing ToolIntent and SiriEvaluations "
            "(Xcode 27.3 or later). Select that Xcode in Settings > Locations > Command Line Tools, "
            "then rerun this command. No configuration was changed.",
            file=sys.stderr,
        )
        return 1

    settings = (
        "MPC_SIRI_AI_PREVIEW = SIRI_AI_PREVIEW\n"
        "MPC_IOS_DEPLOYMENT_TARGET = 27.3\n"
        "MPC_SIRI_TEST_DEPLOYMENT_TARGET = 27.3\n"
    )
    if args.team:
        settings += f"MPC_DEVELOPMENT_TEAM = {args.team}\n"
    config.write_text(settings)
    print("Siri AI preview enabled locally. In Xcode, select Siri Integration and your 27.3 device, then run Product > Test.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
