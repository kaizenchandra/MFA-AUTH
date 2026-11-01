let csrf: { token: string; headerName: string } | undefined;
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  if (method !== "GET" && !csrf) csrf = await api("/auth/csrf");
  const response = await fetch(`/api/v1${path}`, {
    method,
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(method !== "GET" && csrf ? { [csrf.headerName]: csrf.token } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    if (response.status === 403) csrf = undefined;
    const data = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      data.detail ||
        {
          401: "Your session has expired. Sign in again.",
          403: "Access denied. Reauthenticate before changing security settings.",
          429: "Too many attempts. Please wait.",
          503: "A required service is unavailable. Please try again later.",
        }[response.status] ||
        "The request could not be completed.",
    );
  }
  if (response.status === 204 || response.headers.get("content-length") === "0")
    return undefined as T;
  return response.json().catch(() => undefined) as Promise<T>;
}
export type Organization = {
  id: string;
  name: string;
  slug: string;
  role: string;
};
export type Me = {
  id: string;
  name: string;
  email: string;
  mfa: string;
  organizations: Organization[];
};
