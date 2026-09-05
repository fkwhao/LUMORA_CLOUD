// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });
describe("Cloud account refresh regression tests", () => {
  it("LM-004 does not replay account A purchase under refreshed account B", async () => {
    vi.resetModules();
    const storage = new Map<string,string>();
    vi.stubGlobal("window", { localStorage: { getItem: (k: string) => storage.get(k) ?? null, setItem: (k:string,v:string) => storage.set(k,v) } });
    vi.stubGlobal("navigator", { platform: "test" });
    const calls: Array<{path:string,authorization:string|null,body:unknown}> = [];
    const authResponse = (id: string) => ({ tokenType:"Bearer", accessToken:"token-" + id, user:{id,email:id+"@test.invalid",displayName:id,status:"ACTIVE",roles:[]}, accessTokenExpiresAt:"2099-01-01T00:00:00Z",sessionExpiresAt:"2099-01-01T00:00:00Z" });
    vi.stubGlobal("fetch", vi.fn(async (input: string, init?:RequestInit) => {
      const authorization = new Headers(init?.headers).get("Authorization");
      calls.push({path:String(input),authorization,body:init?.body});
      if (input.endsWith("/login")) return Response.json(authResponse("A"));
      if (input.endsWith("/refresh")) return Response.json(authResponse("B"));
      if (authorization === "Bearer token-A") return Response.json({code:"UNAUTHORIZED"},{status:401});
      return Response.json({id:"purchase-for-B",userId:"B"});
    }));
    const auth = await import("../../src/api/auth");
    const displayedUser = await auth.login("A@test.invalid", "test-only");
    const result = await auth.apiFetch<{userId:string}>("/api/app/billing/orders", {method:"POST",body:JSON.stringify({planVersionId:20})}).catch(error => error);
    expect(result).toMatchObject({code:"SESSION_CHANGED"});
    const replayed = calls.filter(c=>c.path.includes("/billing/orders") && c.authorization === "Bearer token-B");
    console.log("REPRO LM-004", JSON.stringify({displayedUser:displayedUser.id,resultCode:result.code,replayedWrites:replayed.length}));
    expect(replayed, "old account business intent must not be silently replayed for another account").toHaveLength(0);
  });
  it("LM-004 refreshes the same account and replays with the same operation key", async () => {
    vi.resetModules();
    const storage = new Map<string, string>();
    vi.stubGlobal("window", { localStorage: { getItem: (key:string) => storage.get(key) ?? null, setItem: (key:string,value:string) => storage.set(key,value) } });
    vi.stubGlobal("navigator", { platform:"test" });
    const writes: RequestInit[] = [];
    const response = (token:string) => ({accessToken:token,user:{id:"7",email:"a@test.invalid",displayName:"A",status:"ACTIVE",roles:[]}});
    vi.stubGlobal("fetch", vi.fn(async (input:string, init:RequestInit) => {
      if (input.endsWith("/login")) return Response.json(response("old-token"));
      if (input.endsWith("/refresh")) return Response.json(response("new-token"));
      writes.push(init);
      return new Headers(init.headers).get("Authorization") === "Bearer old-token"
        ? Response.json({code:"UNAUTHORIZED"}, {status:401})
        : Response.json({orderNo:"SAME-ACCOUNT"});
    }));
    const auth = await import("../../src/api/auth");
    await auth.login("a@test.invalid", "test-only");
    const invalidated = vi.fn();
    const unsubscribe = auth.onSessionInvalidated(invalidated);
    try {
      await expect(auth.apiFetch("/api/app/billing/orders", {
        method:"POST", headers:{"Idempotency-Key":"operation-1"}, body:JSON.stringify({planVersionId:20}),
      })).resolves.toEqual({orderNo:"SAME-ACCOUNT"});
      expect(writes).toHaveLength(2);
      expect(writes.map(write => new Headers(write.headers).get("Idempotency-Key"))).toEqual(["operation-1","operation-1"]);
      expect(writes[0]!.body).toBe(writes[1]!.body);
      expect(invalidated).not.toHaveBeenCalled();
    } finally { unsubscribe(); }
  });

});
