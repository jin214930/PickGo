import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PAGE = __ENV.PAGE || '1';
const SIZE = __ENV.SIZE || '20';
const KEYWORD = __ENV.KEYWORD || '';
const TYPE = __ENV.TYPE || '';
const SORT = __ENV.SORT || 'ID_DESC';
const SLEEP_SEC = Number(__ENV.SLEEP_SEC || '1');

export const options = {
  vus: Number(__ENV.VUS || 30),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

function buildUrl() {
  let url = `${BASE_URL}/api/posts?page=${PAGE}&size=${SIZE}&keyword=${encodeURIComponent(KEYWORD)}&sort=${SORT}`;

  if (TYPE) {
    url += `&type=${TYPE}`;
  }

  return url;
}

export function setup() {
  const url = buildUrl();
  const response = http.get(url);

  check(response, {
    'warmup list status is 200': (r) => r.status === 200,
  });

  return { url };
}

export default function (data) {
  const response = http.get(data.url);

  check(response, {
    'list status is 200': (r) => r.status === 200,
    'list has items': (r) => {
      const body = JSON.parse(r.body);
      return Array.isArray(body.data.items) && body.data.items.length > 0;
    },
  });

  if (SLEEP_SEC > 0) {
    sleep(SLEEP_SEC);
  }
}