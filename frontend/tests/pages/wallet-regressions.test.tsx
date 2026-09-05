import React from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({
  getAdminWallet:vi.fn(),adjustAdminWallet:vi.fn(),getWalletOverview:vi.fn(),createWalletTopup:vi.fn(),
  cancelWalletTopup:vi.fn(),completeMockTopup:vi.fn(),reload:vi.fn(),getPaymentCapabilities:vi.fn(),getPurchaseOrder:vi.fn(),listPurchaseOrders:vi.fn(),cancelPurchaseOrder:vi.fn(),completeMockPayment:vi.fn(),completeWalletPayment:vi.fn(),
  users:[{id:1,displayName:"Alice",email:"alice@test.invalid"},{id:2,displayName:"Bob",email:"bob@test.invalid"}],
}));
vi.mock("@heroui/react", async () => {
  const R = await import("react");
  const Name = R.createContext<string|undefined>(undefined);
  const Wrap = ({children,className}:any) => <div className={className}>{children}</div>;
  const Card = Object.assign(Wrap,{Header:Wrap,Content:Wrap,Title:Wrap,Description:Wrap,Footer:Wrap});
  const TextField = ({children,name}:any) => <Name.Provider value={name}><div>{children}</div></Name.Provider>;
  const Input = ({fullWidth,isRequired,...props}:any) => <input {...props} name={props.name ?? R.useContext(Name)}/>;
  const TextArea = ({fullWidth,...props}:any) => <textarea {...props} name={props.name ?? R.useContext(Name)}/>;
  const Select = Object.assign(({children,name,defaultSelectedKey}:any) => <div><input type="hidden" name={name} value={defaultSelectedKey ?? "CNY"}/>{children}</div>,{Trigger:Wrap,Value:Wrap,Indicator:Wrap,Popover:Wrap});
  return {Card,TextField,Input,TextArea,Select,Label:Wrap,Chip:Wrap,ListBox:Object.assign(Wrap,{Item:Wrap}),
    Button:({children,onPress,isDisabled,type,...p}:any)=><button type={type ?? "button"} disabled={isDisabled} onClick={onPress}>{children}</button>};
});
vi.mock("../../src/features/admin/useAdminUserSearch",()=>({
  useAdminUserSearch:()=>({query:"",setQuery:vi.fn(),users:mocks.users,loading:false,loadingMore:false,error:null,hasMore:false,queryIsValid:true,reload:mocks.reload,loadMore:vi.fn()}),
}));
vi.mock("../../src/api/billing",()=>mocks);
import { AdminWalletsPage } from "../../src/pages/admin/AdminWalletsPage";
import { WalletPage } from "../../src/pages/console/WalletPage";
const wallet = (id=1,minor=10000) => ({accounts:[{accountId:"account-"+id,currency:"CNY",availableMinor:minor,version:1}],ledger:[],topupOrders:[]});
beforeEach(()=>{vi.clearAllMocks();mocks.getAdminWallet.mockImplementation(async(id:number)=>wallet(id,id*10000));mocks.getWalletOverview.mockResolvedValue(wallet());mocks.adjustAdminWallet.mockResolvedValue({});mocks.createWalletTopup.mockResolvedValue({orderNo:"TEST-TOPUP"});mocks.getPaymentCapabilities.mockResolvedValue({availableMethods:["WALLET","MOCK"]});});
afterEach(cleanup);

it("LM-003 reuses one business operation after adjustment succeeds but refresh fails",async()=>{
  render(<AdminWalletsPage/>);
  await screen.findByText("¥100.00");
  const form = document.querySelector("form")!;
  fireEvent.change(form.querySelector('input[name="amount"]')!,{target:{value:"100"}});
  fireEvent.change(form.querySelector('textarea[name="reason"]')!,{target:{value:"reproduction"}});
  mocks.getAdminWallet.mockRejectedValueOnce(new Error("injected query failure"));
  fireEvent.submit(form);
  await screen.findByText("调整已成功，仅钱包刷新失败：无法连接云端服务，请稍后重试");
  expect(screen.getByText("alice@test.invalid 的余额已调整成功")).not.toBeNull();
  expect(form.querySelector<HTMLInputElement>('input[name="amount"]')!.value).toBe("");
  fireEvent.submit(form);
  await act(async()=>{await Promise.resolve();});
  expect(mocks.adjustAdminWallet).toHaveBeenCalledTimes(1);
  const keys=mocks.adjustAdminWallet.mock.calls.map(c=>c[1]);
  console.log("REPRO LM-003",JSON.stringify({submits:keys.length,distinctOperationKeys:new Set(keys).size,amountDeltas:mocks.adjustAdminWallet.mock.calls.map(c=>c[0].amountDelta)}));
  expect(new Set(keys).size,"one already-successful adjustment must not become a second operation").toBe(1);
});


it.each(["100", "-100"])("LM-003 retries an uncertain %s adjustment with the original operation key", async (amount) => {
  mocks.adjustAdminWallet.mockRejectedValueOnce(new TypeError("response lost after write"));
  render(<AdminWalletsPage/>);
  await screen.findByText("¥100.00");
  const form=document.querySelector("form")!;
  fireEvent.change(form.querySelector('input[name="amount"]')!,{target:{value:amount}});
  fireEvent.change(form.querySelector('textarea[name="reason"]')!,{target:{value:"same verified adjustment"}});
  fireEvent.submit(form);
  await screen.findByText("无法连接云端服务，请稍后重试");
  fireEvent.submit(form);
  await screen.findByText("alice@test.invalid 的余额已调整成功");
  expect(mocks.adjustAdminWallet).toHaveBeenCalledTimes(2);
  expect(mocks.adjustAdminWallet.mock.calls[0]![1]).toBe(mocks.adjustAdminWallet.mock.calls[1]![1]);
  expect(mocks.adjustAdminWallet.mock.calls[0]![0]).toEqual(mocks.adjustAdminWallet.mock.calls[1]![0]);
});

it("LM-017 retries an uncertain topup creation without creating a new operation", async () => {
  mocks.createWalletTopup.mockRejectedValueOnce(new TypeError("response lost after create"));
  render(<WalletPage/>);
  await screen.findByText("¥100.00");
  const form=document.querySelector("form")!;
  fireEvent.change(form.querySelector('input[name="amount"]')!,{target:{value:"25"}});
  fireEvent.submit(form);
  await screen.findByText("无法连接云端服务，请稍后重试");
  fireEvent.submit(form);
  await screen.findByText("充值订单 TEST-TOPUP 已创建");
  expect(mocks.createWalletTopup).toHaveBeenCalledTimes(2);
  expect(mocks.createWalletTopup.mock.calls[0]![2]).toBe(mocks.createWalletTopup.mock.calls[1]![2]);
  expect(mocks.createWalletTopup.mock.calls[0]!.slice(0,2)).toEqual(mocks.createWalletTopup.mock.calls[1]!.slice(0,2));
});

it("LM-018 late manual refresh for A cannot replace B wallet",async()=>{
  render(<AdminWalletsPage/>);
  await screen.findByText("¥100.00");
  let resolveA!:(value:any)=>void;
  mocks.getAdminWallet.mockImplementationOnce(()=>new Promise(resolve=>{resolveA=resolve;}));
  fireEvent.click(screen.getByRole("button",{name:"刷新"}));
  fireEvent.click(screen.getByRole("button",{name:/Bob/}));
  await screen.findByText("¥200.00");
  await act(async()=>{resolveA(wallet(1,11100));});
  const body=document.body.textContent!;
  console.log("REPRO LM-018",JSON.stringify({selectedBob:screen.getByRole("button",{name:/Bob/}).className.includes("bg-default/50"),showsA111:body.includes("¥111.00"),showsB200:body.includes("¥200.00")}));
  expect(screen.queryByText("¥200.00"),"B selection must retain B's wallet").not.toBeNull();
});

it("LM-017 successful topup creation refreshes orders without a false failure",async()=>{
  render(<WalletPage/>);
  await screen.findByText("¥100.00");
  const form=document.querySelector("form")!;
  fireEvent.change(form.querySelector('input[name="amount"]')!,{target:{value:"25"}});
  fireEvent.submit(form);
  await waitFor(()=>expect(mocks.createWalletTopup).toHaveBeenCalledTimes(1));
  await act(async()=>{await Promise.resolve();});
  console.log("REPRO LM-017",JSON.stringify({created:mocks.createWalletTopup.mock.calls.length,overviewLoads:mocks.getWalletOverview.mock.calls.length,falseFailure:!!screen.queryByText("无法连接云端服务，请稍后重试")}));
  expect(screen.queryByText("无法连接云端服务，请稍后重试"),"successful order creation should not be shown as failed").toBeNull();
  expect(mocks.getWalletOverview).toHaveBeenCalledTimes(2);
  expect(screen.getByText("充值订单 TEST-TOPUP 已创建")).not.toBeNull();
});

import { PurchaseOrdersPage } from "../../src/pages/console/PurchaseOrdersPage";
const order = (status="PENDING_PAYMENT") => ({
  orderNo:"REPRO-ORDER",planCode:"pro",planName:"Pro",planVersionId:20,
  amountMinor:9900,currency:"CNY",status,createdAt:"2026-09-04T00:00:00Z",
  expiresAt:"2026-09-04T01:00:00Z",mockPaymentEnabled:false,
  ...(status==="FULFILLED"?{paidAt:"2026-09-04T00:01:00Z",paymentProvider:"WALLET",subscriptionId:"queued-renewal"}:{}),
});
it("LM-017 wallet checkout does not claim no payment method when wallet is available",async()=>{
  mocks.getPaymentCapabilities.mockResolvedValue({mockPaymentEnabled:false,availableMethods:["WALLET"]});
  mocks.getPurchaseOrder.mockResolvedValue(order());
  render(<PurchaseOrdersPage orderNo="REPRO-ORDER"/>);
  await screen.findByRole("button",{name:/钱包余额支付/});
  const falseUnavailable=screen.queryByText("当前环境没有可用支付方式，订单仍会保留到截止时间。");
  console.log("REPRO LM-017 capabilities",JSON.stringify({walletButton:true,falseUnavailable:!!falseUnavailable}));
  expect(falseUnavailable).toBeNull();
});
it("LM-017 disabled mock capability does not expose a mock topup payment action",async()=>{
  mocks.getWalletOverview.mockResolvedValue({...wallet(),topupOrders:[{orderNo:"TOPUP",amountMinor:2500,currency:"CNY",status:"PENDING_PAYMENT",createdAt:"2026-09-04T00:00:00Z"}]});
  mocks.completeMockTopup.mockRejectedValue(new Error("MOCK_PAYMENT_DISABLED"));
  mocks.getPaymentCapabilities.mockResolvedValue({availableMethods:["WALLET"]});
  render(<WalletPage/>);
  await screen.findByText("¥100.00");
  expect(mocks.completeMockTopup).not.toHaveBeenCalled();
  console.log("REPRO LM-017 mock-disabled",JSON.stringify({mockActionVisible:!!screen.queryByRole("button",{name:"模拟支付"}),rejectedMockCalls:mocks.completeMockTopup.mock.calls.length}));
  expect(screen.queryByRole("button",{name:"模拟支付"})).toBeNull();
});
it("LM-015 fulfilled renewal shows granted rights and scheduled dates",async()=>{
  mocks.getPaymentCapabilities.mockResolvedValue({mockPaymentEnabled:false,availableMethods:["WALLET"]});
  mocks.getPurchaseOrder.mockResolvedValue({...order("FULFILLED"),subscription:{subscriptionId:"queued-renewal",status:"ACTIVE",startsAt:"2099-10-01T00:00:00Z",endsAt:"2099-10-31T00:00:00Z"}});
  render(<PurchaseOrdersPage orderNo="REPRO-ORDER"/>);
  await screen.findByText("queued-renewal");
  const fields=Array.from(document.querySelectorAll("dt")).map(n=>n.textContent);
  console.log("REPRO LM-015 order fields",JSON.stringify(fields));
  expect(fields).toContain("权益开始时间");
  expect(fields).toContain("权益结束时间");
  expect(await screen.findByText("待生效，权益已到账")).toBeTruthy();
});
