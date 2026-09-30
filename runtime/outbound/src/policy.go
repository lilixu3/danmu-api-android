package main

// Transport capabilities are selected per exact hostname, not per source.
// Only Bahamut may use the previously verified shared Cloudflare ECH path.
// Other ECH targets must obtain their own HTTPS record (including aliases).
type targetPolicy struct {
	source     string
	requireECH bool
	sharedECH  bool
}

var targetPolicies = map[string]targetPolicy{
	"api.gamer.com.tw":         {source: "bahamut", requireECH: true, sharedECH: true},
	"api.tmdb.org":             {source: "tmdb"},
	"api.themoviedb.org":       {source: "tmdb"},
	"api.danmaku.weeblify.app": {source: "dandan", requireECH: true},
	"nipaplay.aimes-soft.com":  {source: "dandan"},
	"api.animeko.org":          {source: "animeko"},
	"danmaku-global.myani.org": {source: "animeko", requireECH: true},
	"danmaku-cn.myani.org":     {source: "animeko"},
	"s1.animeko.openani.org":   {source: "animeko"},
	"api.bangumi.vip":          {source: "animeko", requireECH: true},
}
