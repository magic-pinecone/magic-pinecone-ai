"""MCP server exposing campus places to AI clients.

Run over stdio with `uv run python -m app.mcp_server`. It reads the bundled
place list directly, so it needs neither the database nor the API running.
"""

from mcp.server.mcpserver import MCPServer

from app.services import campus_places
from app.services.campus_places import PlaceCategory

mcp = MCPServer("ncu-campus")


@mcp.tool()
def find_place(query: str = "", category: PlaceCategory | None = None) -> dict:
    """Find buildings and resources on the NCU campus and how to get to them.

    `query` matches a Chinese or English name, a building code such as "E6",
    or an alias such as "OIA"; leave it empty to list a whole category. Each
    result has coordinates and links that open walking directions in Google
    or Apple Maps. An empty result means the place is not in the list, not
    that it does not exist. Show the attribution when you show the results.
    """
    return {
        "attribution": campus_places.load().attribution,
        "places": [
            place.model_dump() for place in campus_places.search(query, category)
        ],
    }


if __name__ == "__main__":
    mcp.run()
