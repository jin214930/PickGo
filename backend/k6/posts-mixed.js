import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LIST_SIZE = __ENV.SIZE || '100';
const HOT_POST_COUNT = Number(__ENV.HOT_POST_COUNT || 1);
const LIST_RATIO = Number(__ENV.LIST_RATIO || '0.7');

export const options = {
  vus: Number(__ENV.VUS || 100),
  duration: __ENV.DURATION || '15s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const listUrl = `${BASE_URL}/api/posts?page=1&size=${LIST_SIZE}&keyword=&sort=ID_DESC`;
  const listResponse = http.get(listUrl);

  check(listResponse, {
    'mixed setup list status is 200': (r) => r.status === 200,
  });

  const body = JSON.parse(listResponse.body);
  const ids = body.data.items.slice(0, HOT_POST_COUNT).map((item) => item.id);

  ids.forEach((id) => {
    const warmup = http.get(`${BASE_URL}/api/posts/${id}`);
    check(warmup, {
      'mixed warmup detail status is 200': (r) => r.status === 200,
    });
  });

  return { listUrl, ids };
}

export default function (data) {
  if (Math.random() < LIST_RATIO) {
    const response = http.get(data.listUrl);
    check(response, {
      'mixed list status is 200': (r) => r.status === 200,
    });
    return;
  }

  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const response = http.get(`${BASE_URL}/api/posts/${id}`);
  check(response, {
    'mixed detail status is 200': (r) => r.status === 200,
  });
}